import uvicorn

import os
import ffmpeg
import logging
import io
from pydantic import BaseModel
from fastapi import FastAPI, Request 
from fastapi.responses import JSONResponse
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from dotenv import load_dotenv
from rembg import remove, new_session
from PIL import Image, ImageDraw
from transformers import AutoImageProcessor, DeformableDetrForObjectDetection
import torch
from contextlib import asynccontextmanager

# CONFIGS
load_dotenv()


STORAGE_DIR = os.getenv("STORAGE_DATA_DIR", "/tmp/imageprocessing/data")
ml_models = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    ml_models["rembg"] = new_session("isnet-general-use")
    processor = AutoImageProcessor.from_pretrained("SenseTime/deformable-detr-with-box-refine")
    model = DeformableDetrForObjectDetection.from_pretrained("SenseTime/deformable-detr-with-box-refine")
    model.eval()
    ml_models["detr_processor"] = processor
    ml_models["detr_model"] = model
    yield
    # shutdown
    ml_models.clear()

app = FastAPI(lifespan=lifespan)

logging.basicConfig(
        filename='app.log',
        level=logging.DEBUG,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )

logger = logging.getLogger(__name__)

class ProcessRequest(BaseModel):
    source_sk: str 
    input_format: str 
    target_sk: str 
    output_format: str

class BackgroundRemovalRequest(BaseModel):
    source_sk: str 
    target_sk: str 

class ObjectDetectionRequest(BaseModel):
    source_sk: str
    target_sk: str

FFMPEG_CODEC_MAP = {
    "jpg":  ("mjpeg", "image2"),
    "jpeg": ("mjpeg", "image2"),
    "png":  ("png",   "image2"),
    "gif":  ("gif",   "gif"),
    "webp": ("libwebp", "webp"),
    "bmp":  ("bmp",   "image2"),
}

# HELPERS

def safe_path(sk: str) -> str:
    storage_dir = os.getenv("STORAGE_DATA_DIR", "/tmp/imageprocessing/data")
    path = os.path.realpath(os.path.join(storage_dir, sk))
    if not path.startswith(os.path.realpath(storage_dir)):
        raise ValueError(f"Invalid storage key: {sk}")
    return path

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content=jsonable_encoder({"errors": exc.errors()})
    )

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
            status_code=500,
            content={"message": f'An internal server error occurred {exc}'}
            )   


def draw_boxes(image_bytes, results, id2label):
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    draw = ImageDraw.Draw(image)
    for score, label, box in zip(results["scores"], results["labels"], results["boxes"]):
        box = [round(i, 2) for i in box.tolist()]
        draw.rectangle(box, outline="red", width=3)
        draw.text((box[0], box[1]), f"{id2label[label.item()]} {round(score.item(), 2)}", fill="red")
    output = io.BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()

# ENDPOINTS
@app.get("/health")
async def health():
    logger.info("health request")
    return JSONResponse(content={"status": "ok", "models": list(ml_models.keys())}, status_code=200)


@app.post("/convert_format")
def convert_format(request: ProcessRequest):
    try:
        src = safe_path(request.source_sk)
        tgt = safe_path(request.target_sk)
        codec, container = FFMPEG_CODEC_MAP.get(
            request.output_format.lower(),
            (request.output_format.lower(), request.output_format.lower())
        )
        ffmpeg.input(src) \
              .output(tgt, vcodec=codec, f=container) \
              .run(capture_stdout=True, capture_stderr=True)
        logger.info(f'converted {src} to {tgt}')
        return JSONResponse(content={"target_sk": request.target_sk}, status_code=200)
    except Exception as e:
        stderr = getattr(e, 'stderr', b'')
        if stderr:
            logger.error(f'ffmpeg failed: {stderr.decode()}')
        else:
            logger.error(f'ffmpeg failed: {e}')
        raise

@app.post("/remove_background")
def remove_background(request: BackgroundRemovalRequest):
    try:
        session = ml_models["rembg"]
        with open(safe_path(request.source_sk), 'rb') as i:
            with open(safe_path(request.target_sk), 'wb') as o:
                input_data = i.read()
                output = remove(input_data, session=session)
                o.write(output)
        return JSONResponse(content={"target_sk": request.target_sk}, status_code=200)
    except FileNotFoundError:
        logger.error(f'source file not found: {request.source_sk}')
        raise
    except Exception as e:
        logger.error(f'background removal failed: {e}')
        raise


@app.post("/detect_objects")
def detect_objects(request: ObjectDetectionRequest):
    try:
        src = safe_path(request.source_sk)
        tgt = safe_path(request.target_sk)

        processor = ml_models["detr_processor"]
        model = ml_models["detr_model"]

        with open(src, "rb") as f:
            image_bytes = f.read()

        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        inputs = processor(images=image, return_tensors="pt")

        with torch.no_grad():
            outputs = model(**inputs)

        target_sizes = torch.tensor([image.size[::-1]])
        threshold = float(os.getenv("DETECTION_THRESHOLD", "0.5"))
        results = processor.post_process_object_detection(
            outputs,
            target_sizes=target_sizes,
            threshold=threshold
        )[0]

        annotated = draw_boxes(image_bytes, results, model.config.id2label)

        with open(tgt, "wb") as f:
            f.write(annotated)

        logger.info(f'detected objects in {src}, output written to {tgt}')
        return JSONResponse(content={"target_sk": request.target_sk}, status_code=200)

    except FileNotFoundError:
        logger.error(f'source file not found: {request.source_sk}')
        raise
    except Exception as e:
        logger.error(f'object detection failed: {e}')
        raise

if __name__ == "__main__":
    port = int(os.getenv("WORKER_PORT", "8082"))
    uvicorn.run(app, host="0.0.0.0", port=port)
