# 18 AI Worker service implementation (task: 26)

> Prerequisites: task 23 (repo restructure), task 24 (common module), and task 34
> (gateway and orchestrator) completed. Worker is called by the orchestrator for
> all AI and processing jobs.

---

## Context

The AI worker is the compute-intensive service responsible for all file processing
operations. It receives job requests from the orchestrator, executes the processing
(either deterministic via FFmpeg or AI-driven via a HuggingFace model), writes the
output file to the shared storage directory, and returns the output storage key to
the orchestrator.

This service is the primary justification for the microservices architecture: it is
CPU and RAM intensive, written in a different language (Python) from the Java services,
and is the only service that needs to scale horizontally under load. The gateway and
orchestrator can remain at one replica while worker replicas are added independently.

---

## Architecture

```
Orchestrator
  |
  |-- HTTP POST /convert_format    (port 8082)
  |-- HTTP POST /remove_background (port 8082)
  |-- HTTP POST /detect_objects    (port 8082)
  v
AI Worker
  |-- FFmpeg          (format conversion)
  |-- rembg           (background removal, isnet-general-use model)
  |-- Deformable DETR (object detection, SenseTime/deformable-detr-with-box-refine)
  v
Shared storage directory (read source, write output)
```

The worker is entirely internal — it is never called by the browser directly and has
no public-facing port in production. It reads from and writes to the same storage
directory as the orchestrator, using UUID-based storage keys to identify files.

---

## Technology choices

**Python + FastAPI** — Python is the natural choice for AI inference since the
HuggingFace ecosystem (`transformers`, `rembg`) is Python-first. FastAPI provides
a lightweight, async-capable HTTP server with automatic request validation via
Pydantic and built-in OpenAPI documentation.

**Uvicorn** — ASGI server used to run the FastAPI application. Chosen over Gunicorn
for simplicity in this phase; Gunicorn with Uvicorn workers would be more appropriate
for production under concurrent load.

**ffmpeg-python** — Python wrapper around the FFmpeg binary providing a fluent API
for building FFmpeg command pipelines. Chosen over raw `subprocess` calls because it
handles argument construction cleanly, exposes stdout/stderr on error, and is more
readable. FFmpeg itself is a system dependency installed separately.

**rembg** — Python library for background removal using the `isnet-general-use` model.
Chosen over direct HuggingFace `transformers` integration for its simplicity: a single
function call handles model inference, pre-processing, and post-processing. The
underlying model weights are downloaded on first run and cached locally.

**Deformable DETR** — object detection model from SenseTime, accessed via the
HuggingFace `transformers` library. An encoder-decoder transformer with a ResNet-50
backbone and box refinement, trained on COCO 2017 (118k images, 80 object classes).
Chosen for its strong detection accuracy, well-maintained HuggingFace integration,
and structured output (bounding boxes + class labels + confidence scores). The model
runs on CPU — inference takes 5–15 seconds per image on CPU, which makes the async
job processing pattern visually demonstrable in the UI.

**torch + torchvision** — required by the `transformers` library for model inference.
Installed from the PyTorch CPU index to avoid pulling in GPU-specific binaries.
The `explicit = true` flag in `pyproject.toml` ensures only `torch` and `torchvision`
are fetched from the PyTorch index; all other packages including `tqdm` resolve from
PyPI, avoiding version conflicts with `rembg`.

**Pillow** — used to open images for DETR inference and to draw annotated bounding
boxes on the output image.

**uv** — Python package manager used instead of pip/poetry. Faster dependency
resolution, built-in virtual environment management, and support for dependency
groups (separating production from development dependencies).

---

## Startup and model loading

All models are loaded once at application startup using FastAPI's `lifespan` context
manager, which replaced the deprecated `@app.on_event("startup")` pattern in recent
FastAPI versions. Sessions and model objects are stored in a module-level dictionary
(`ml_models`) and accessed from endpoint handlers.

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    ml_models["rembg"] = new_session("isnet-general-use")
    processor = AutoImageProcessor.from_pretrained("SenseTime/deformable-detr-with-box-refine")
    model = DeformableDetrForObjectDetection.from_pretrained("SenseTime/deformable-detr-with-box-refine")
    model.eval()
    ml_models["detr_processor"] = processor
    ml_models["detr_model"] = model
    yield
    ml_models.clear()
```

The structure is flat — no nested context managers, a single `yield` after all models
are loaded. This is important: nesting `async with` blocks inside `lifespan` produces
a `TypeError` at startup because the inner context manager is not a valid async
iterator in this context.

Loading at startup rather than on first request means:
- The first request is not penalised by model load time
- The application is not ready to serve until all models are loaded
- In Kubernetes, the readiness probe will not pass until startup completes,
  naturally preventing traffic from reaching an unready pod

On first run model weights are downloaded from HuggingFace (~200MB for rembg,
~164MB for DETR). On subsequent runs they are loaded from the local cache
(`~/.cache/huggingface`). In Docker this cache path should be mounted as a named
volume to avoid re-downloading on every container restart.

---

## Storage model

The worker shares a storage directory with the orchestrator. Both services read the
base path from the `STORAGE_DATA_DIR` environment variable, defaulting to
`/tmp/imageprocessing/data` for local development.

Files are identified by UUID-based storage keys, not by paths. The orchestrator
passes source and target storage keys in the request body. The worker resolves these
keys to full filesystem paths using `safe_path()` before performing any I/O.

`safe_path()` validates that every resolved path starts with the configured base
directory using `os.path.realpath`, preventing path traversal attacks where a
malicious key such as `../../etc/passwd` could escape the storage directory. This
mirrors the equivalent validation in the Java `StorageService`.

The environment variable is read inside `safe_path()` at call time rather than at
module load time, so test fixtures can override it with `monkeypatch.setenv` without
the value being captured at import.

---

## API endpoints

### `GET /health`

Returns the application status and the list of loaded models. Used by the orchestrator
and later by Kubernetes liveness and readiness probes to verify the service is
operational and all models are loaded.

### `POST /convert_format`

Accepts a `ProcessRequest` body with `source_sk`, `input_format`, `target_sk`, and
`output_format`. Invokes FFmpeg to convert the source file to the target format.
FFmpeg auto-detects the input format from the file's magic bytes — no `-f` flag is
passed on the input side since storage keys have no file extension. On the output
side, `vcodec` and `f` (muxer) are specified explicitly via `FFMPEG_CODEC_MAP`,
which translates canonical format names (`jpg`, `png`) to FFmpeg-specific codec and
container names (`mjpeg`/`image2`, `png`/`image2`). This translation is the only
place in the system where FFmpeg internals are referenced.

Returns `{"target_sk": "<key>"}` on success. On FFmpeg failure the exception is
re-raised and caught by the global exception handler, which returns 500.

### `POST /remove_background`

Accepts a `BackgroundRemovalRequest` body with `source_sk` and `target_sk`. Reads
the source file, runs the rembg model inference, and writes the output PNG to the
target path.

Returns `{"target_sk": "<key>"}` on success. On file not found or model failure
the exception is re-raised and the global handler returns 500.

### `POST /detect_objects`

Accepts an `ObjectDetectionRequest` body with `source_sk` and `target_sk`. Reads
the source image, runs Deformable DETR inference, draws annotated bounding boxes on
the image using Pillow, and writes the annotated PNG to the target path.

The detection confidence threshold defaults to `0.5` and is configurable via the
`DETECTION_THRESHOLD` environment variable. The output is always PNG regardless of
the input format since the annotation step produces a new image via Pillow.

Returns `{"target_sk": "<key>"}` on success. On file not found or model failure
the exception is re-raised and the global handler returns 500.

---

## Request models

Three separate Pydantic models are defined, one per endpoint. This makes the contract
explicit per operation and prevents accidentally sending format fields to endpoints
that do not need them.

`ProcessRequest` — used by `/convert_format`. Fields: `source_sk`, `input_format`,
`target_sk`, `output_format`.

`BackgroundRemovalRequest` — used by `/remove_background`. Fields: `source_sk`,
`target_sk` only.

`ObjectDetectionRequest` — used by `/detect_objects`. Fields: `source_sk`,
`target_sk` only. No format fields since the output is always PNG.

---

## Format normalization

A single canonical format name flows through the entire system. `ImageFormatValidator`
in the orchestrator maps MIME types to canonical names at upload time
(`image/jpeg` → `jpg`). The UI sends the same canonical names as target format
values. The worker's `FFMPEG_CODEC_MAP` is the only translation point between
canonical names and FFmpeg-specific codec/muxer identifiers.

| Layer                   | Value stored/sent          |
|-------------------------|----------------------------|
| Database (format col)   | `jpg`                      |
| JobRequest.targetFormat | `jpg`                      |
| ConvertFormatRequest    | `jpg`                      |
| FFMPEG_CODEC_MAP input  | `jpg` → `mjpeg` + `image2` |

`jpeg` never appears outside the worker's codec map defensive fallback.

---

## Error handling

**Endpoint-level** — specific exceptions are caught and logged with context before
being re-raised. FFmpeg failures are logged with the decoded stderr output.
`FileNotFoundError` is caught separately in endpoints that read source files.
General `Exception` is caught as a fallback with the error message logged.

**Application-level** — two global exception handlers are registered:

`RequestValidationError` handler — returns 422 with the Pydantic validation error
details serialized via `jsonable_encoder`. Plain `json.dumps` cannot serialize the
error payload because it may contain raw bytes from the request body; `jsonable_encoder`
handles this transparently.

`Exception` handler — catch-all returning 500 with the exception message. Ensures
all unhandled exceptions produce a JSON response rather than an empty 500.

**TestClient behaviour** — FastAPI's `TestClient` re-raises server exceptions by
default, which causes tests asserting 500 responses to fail. Setting
`raise_server_exceptions=False` on the `TestClient` fixture makes it return the
actual HTTP response instead, enabling 500 assertions to work correctly.

---

## Endpoint design decisions

**Synchronous handlers** — all endpoint functions are plain `def`, not `async def`.
FFmpeg invocation, file I/O, and model inference are all blocking operations that
would block the event loop if run in an `async` function. FastAPI automatically runs
plain `def` functions in a thread pool executor, keeping the event loop free for
other requests.

**CPU-only inference** — all models run on CPU. No GPU is required or configured.
This keeps the Docker image and Kubernetes pod spec simple and avoids GPU quota
constraints on cloud providers. The `torch.no_grad()` context manager is used during
DETR inference to disable gradient computation, reducing memory usage.

**Return original key not resolved path** — endpoints return `request.target_sk`
(the original UUID key) rather than the full resolved filesystem path from
`safe_path()`. The orchestrator already knows the storage key and uses it to update
the database; returning the full path would leak internal filesystem structure.

**Object detection output is always PNG** — the annotated image produced by
`draw_boxes` is always saved as PNG via Pillow, regardless of the input format.
This is a deliberate simplification: the annotation step creates a new image rather
than converting the original, and PNG is a lossless format suitable for annotated
output.

---

## Dependency management

Production and development dependencies are separated using uv dependency groups.
The PyTorch CPU index is registered as an explicit source so it is only used for
`torch` and `torchvision`, preventing version conflicts with other packages:

```toml
[project.dependencies]
fastapi, uvicorn, ffmpeg-python, rembg, torch, torchvision,
transformers, timm, pillow, python-dotenv

[dependency-groups.dev]
pytest, httpx

[[tool.uv.index]]
url = "https://download.pytorch.org/whl/cpu"
name = "pytorch-cpu"
explicit = true

[tool.uv.sources]
torch = [{ index = "pytorch-cpu" }]
torchvision = [{ index = "pytorch-cpu" }]
```

The `explicit = true` flag is critical — without it uv uses the PyTorch index as a
primary source for all packages including `tqdm`, which conflicts with rembg's version
requirement. With `explicit = true` only torch and torchvision are fetched from the
PyTorch index and everything else resolves from PyPI normally.

The Docker image uses `uv sync --no-dev` to exclude test dependencies from the
production image, reducing image size and attack surface.

---

## Testing

Unit and integration tests use FastAPI's `TestClient` backed by `httpx`. The rembg
model session and DETR model/processor are replaced with `MagicMock` instances via
`monkeypatch.setitem` before every test, preventing any model download or load during
the test suite. The storage directory is redirected to a `tmp_path` fixture directory
via `monkeypatch.setenv`, isolating test file I/O from the real storage path.

Test coverage includes health check responses, `safe_path` traversal protection,
successful format conversion, background removal and object detection, model failures
producing 500, missing source files producing 500, and malformed request bodies
producing 422.

---

## Local development

The worker runs independently of the Java services and can be started and tested
in isolation.

```
cd worker
uv sync
uv run python main.py
```

| Variable             | Default                        | Purpose                                |
|----------------------|--------------------------------|----------------------------------------|
| `WORKER_PORT`        | `8082`                         | Port the uvicorn server binds to       |
| `STORAGE_DATA_DIR`   | `/tmp/imageprocessing/data`    | Shared file storage base path          |
| `DETECTION_THRESHOLD`| `0.5`                          | Minimum confidence for object detection|
| `WORKER_URL`         | `http://localhost:8082`        | Configured in orchestrator, not worker |

The health endpoint confirms the service is running and all models are loaded:

```
curl http://localhost:8082/health
```

Expected response when fully initialised:

```json
{"status": "ok", "models": ["rembg", "detr_processor", "detr_model"]}
```

To run the test suite:

```
uv run pytest test/ -v
```
