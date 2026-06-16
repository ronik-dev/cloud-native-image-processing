import uvicorn
import os
import ffmpeg
import logging
from pydantic import BaseModel
from fastapi import FastAPI, Request 
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from dotenv import load_dotenv
from rembg import remove, new_session
from contextlib import asynccontextmanager

# CONFIGS
load_dotenv()


STORAGE_DIR = os.getenv("STORAGE_DATA_DIR", "/tmp/imageprocessing/data")
ml_models = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    ml_models["rembg"] = new_session("isnet-general-use")
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

# HELPERS

def safe_path(sk: str) -> str:
    path = os.path.realpath(os.path.join(STORAGE_DIR, sk))
    if not path.startswith(os.path.realpath(STORAGE_DIR)):
        raise ValueError(f"Invalid storage key: {sk}")
    return path

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
            status_code=422,
            content={"errors": exc.errors()}
            )   

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
            status_code=500,
            content={"message": f'An internal server error occurred {exc}'}
            )   

# ENDPOINTS
@app.get("/health")
async def health():
    logger.info("health request")
    return JSONResponse(content={"status": "ok", "models": list(ml_models.keys())}, status_code=200)

@app.post("/convert_format")
def convert_format(request: ProcessRequest):
    try:
        ffmpeg.input(safe_path(request.source_sk), format=request.input_format) \
                .output(safe_path(request.target_sk), format=request.output_format) \
                .run(capture_stdout=True, capture_stderr=True)
        logger.info(f'converted {safe_path(request.source_sk)} to {safe_path(request.target_sk)}')
        return JSONResponse(content={"target_sk": safe_path(request.target_sk)}, status_code=200)
    except ffmpeg.Error as e:
        logger.error(f'ffmpeg failed: {e.stderr.decode()}')
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
        return JSONResponse(content={"target_sk": safe_path(request.target_sk)}, status_code=200)
    except FileNotFoundError:
        logger.error(f'source file not found: {safe_path(request.source_sk)}')
        raise
    except Exception as e:
        logger.error(f'background removal failed: {e}')
        raise

if __name__ == "__main__":
    port = int(os.getenv("WORKER_PORT", "8082"))
    uvicorn.run(app, host="0.0.0.0", port=port)
