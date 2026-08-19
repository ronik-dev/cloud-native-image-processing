import asyncio
import json
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
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

# CONFIGS
load_dotenv()


STORAGE_DIR = os.getenv("STORAGE_DATA_DIR", "/tmp/imageprocessing/data")
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
JOB_REQUESTS_TOPIC = os.getenv("KAFKA_JOB_REQUESTS_TOPIC", "job.requests")
JOB_RESULTS_TOPIC = os.getenv("KAFKA_JOB_RESULTS_TOPIC", "job.results")
CONSUMER_GROUP_ID = os.getenv("KAFKA_CONSUMER_GROUP", "ai-worker")

ml_models = {}
kafka_state = {}


logging.basicConfig(
        filename='app.log',
        level=logging.DEBUG,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Core processing functions
#
# These used to live inline inside the FastAPI endpoint handlers. They're
# pulled out here so both the HTTP endpoints (kept for manual testing / the
# existing pytest suite) and the Kafka consumer loop below can call the same
# logic. Each raises on failure and returns the target storage key on success.
# ---------------------------------------------------------------------------

FFMPEG_CODEC_MAP = {
    "jpg":  ("mjpeg", "image2"),
    "jpeg": ("mjpeg", "image2"),
    "png":  ("png",   "image2"),
    "gif":  ("gif",   "gif"),
    "webp": ("libwebp", "webp"),
    "bmp":  ("bmp",   "image2"),
}


def safe_path(sk: str) -> str:
    storage_dir = os.getenv("STORAGE_DATA_DIR", "/tmp/imageprocessing/data")
    path = os.path.realpath(os.path.join(storage_dir, sk))
    if not path.startswith(os.path.realpath(storage_dir)):
        raise ValueError(f"Invalid storage key: {sk}")
    return path


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


def _convert_format(source_sk: str, input_format: str, target_sk: str, output_format: str) -> str:
    src = safe_path(source_sk)
    tgt = safe_path(target_sk)
    codec, container = FFMPEG_CODEC_MAP.get(
        output_format.lower(),
        (output_format.lower(), output_format.lower())
    )
    try:
        ffmpeg.input(src) \
              .output(tgt, vcodec=codec, f=container) \
              .run(capture_stdout=True, capture_stderr=True)
    except Exception as e:
        stderr = getattr(e, 'stderr', b'')
        if stderr:
            logger.error(f'ffmpeg failed: {stderr.decode()}')
        else:
            logger.error(f'ffmpeg failed: {e}')
        raise
    logger.info(f'converted {src} to {tgt}')
    return target_sk


def _remove_background(source_sk: str, target_sk: str) -> str:
    session = ml_models["rembg"]
    try:
        with open(safe_path(source_sk), 'rb') as i, open(safe_path(target_sk), 'wb') as o:
            output = remove(i.read(), session=session)
            o.write(output)
    except FileNotFoundError:
        logger.error(f'source file not found: {source_sk}')
        raise
    except Exception as e:
        logger.error(f'background removal failed: {e}')
        raise
    return target_sk


def _detect_objects(source_sk: str, target_sk: str) -> str:
    src = safe_path(source_sk)
    tgt = safe_path(target_sk)
    try:
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
        return target_sk
    except FileNotFoundError:
        logger.error(f'source file not found: {source_sk}')
        raise
    except Exception as e:
        logger.error(f'object detection failed: {e}')
        raise


JOB_DISPATCH = {
    "FORMAT_CONVERSION": lambda p: _convert_format(
        p["source_sk"], p.get("input_format"), p["target_sk"], p.get("output_format")
    ),
    "BACKGROUND_REMOVAL": lambda p: _remove_background(p["source_sk"], p["target_sk"]),
    "OBJECT_DETECTION": lambda p: _detect_objects(p["source_sk"], p["target_sk"]),
}


# ---------------------------------------------------------------------------
# Kafka consumer loop
# ---------------------------------------------------------------------------

async def consume_job_requests():
    """
    Background task started in the FastAPI lifespan. Consumes job.requests,
    runs the matching processing function on a worker thread (they're all
    blocking: subprocess calls, torch inference), and publishes the outcome
    to job.results. Offsets are committed manually, only after the result has
    been produced, so a crash mid-processing causes redelivery rather than a
    silently lost job.
    """
    consumer: AIOKafkaConsumer = kafka_state["consumer"]
    producer: AIOKafkaProducer = kafka_state["producer"]
    loop = asyncio.get_running_loop()

    async for msg in consumer:
        job_id = None
        try:
            payload = json.loads(msg.value)
            job_id = payload.get("job_id")
            job_type = payload.get("job_type")

            logger.info(f"consuming job {job_id} ({job_type})")

            handler = JOB_DISPATCH.get(job_type)
            if handler is None:
                raise ValueError(f"Unknown job_type: {job_type}")

            target_sk = await loop.run_in_executor(None, handler, payload)
            result = {"job_id": job_id, "status": "DONE", "target_sk": target_sk, "error_message": None}

        except Exception as e:
            logger.error(f"job {job_id} failed: {e}")
            result = {"job_id": job_id, "status": "FAILED", "target_sk": None, "error_message": str(e)}

        if job_id is not None:
            await producer.send_and_wait(
                JOB_RESULTS_TOPIC,
                key=str(job_id).encode("utf-8"),
                value=json.dumps(result).encode("utf-8"),
            )
        else:
            # Message wasn't even valid JSON / had no job_id, nothing sane to
            # report back. Logged above; commit and move on rather than
            # looping on a message that can never succeed.
            logger.error(f"discarding unparseable message on {JOB_REQUESTS_TOPIC}")

        await consumer.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup - ML models
    ml_models["rembg"] = new_session("isnet-general-use")
    processor = AutoImageProcessor.from_pretrained("SenseTime/deformable-detr-with-box-refine")
    model = DeformableDetrForObjectDetection.from_pretrained("SenseTime/deformable-detr-with-box-refine")
    model.eval()
    ml_models["detr_processor"] = processor
    ml_models["detr_model"] = model

    # startup - Kafka
    producer = AIOKafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS)
    consumer = AIOKafkaConsumer(
        JOB_REQUESTS_TOPIC,
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        group_id=CONSUMER_GROUP_ID,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
    await producer.start()
    await consumer.start()
    kafka_state["producer"] = producer
    kafka_state["consumer"] = consumer
    kafka_state["consume_task"] = asyncio.create_task(consume_job_requests())

    yield

    # shutdown - Kafka
    kafka_state["consume_task"].cancel()
    try:
        await kafka_state["consume_task"]
    except asyncio.CancelledError:
        pass
    await consumer.stop()
    await producer.stop()

    # shutdown - ML models
    ml_models.clear()


app = FastAPI(lifespan=lifespan)


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


# ---------------------------------------------------------------------------
# HTTP endpoints, kept for manual testing and the existing pytest suite.
# In production, the orchestrator no longer calls these directly; it publishes
# to job.requests and these code paths are only reached via consume_job_requests.
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    consumer_alive = "consumer" in kafka_state and not kafka_state["consume_task"].done()
    logger.info("health request")
    return JSONResponse(
        content={"status": "ok", "models": list(ml_models.keys()), "kafka_consumer_running": consumer_alive},
        status_code=200,
    )


@app.post("/convert_format")
def convert_format(request: ProcessRequest):
    target_sk = _convert_format(request.source_sk, request.input_format, request.target_sk, request.output_format)
    return JSONResponse(content={"target_sk": target_sk}, status_code=200)


@app.post("/remove_background")
def remove_background(request: BackgroundRemovalRequest):
    target_sk = _remove_background(request.source_sk, request.target_sk)
    return JSONResponse(content={"target_sk": target_sk}, status_code=200)


@app.post("/detect_objects")
def detect_objects(request: ObjectDetectionRequest):
    target_sk = _detect_objects(request.source_sk, request.target_sk)
    return JSONResponse(content={"target_sk": target_sk}, status_code=200)


if __name__ == "__main__":
    port = int(os.getenv("WORKER_PORT", "8082"))
    uvicorn.run(app, host="0.0.0.0", port=port)
