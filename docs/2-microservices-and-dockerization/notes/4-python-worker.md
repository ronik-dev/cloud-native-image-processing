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
  |`- HTTP POST (port 8082)
  v
AI Worker
  |
  |`- FFmpeg (system binary, format conversion)
  |`- rembg / HuggingFace model (background removal)
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

**uv** — Python package manager used instead of pip/poetry. Faster dependency
resolution, built-in virtual environment management, and support for dependency
groups (separating production from development dependencies).

---

## Startup and model loading

The rembg model session is loaded once at application startup using FastAPI's
`lifespan` context manager, which replaced the deprecated `@app.on_event("startup")`
pattern in recent FastAPI versions. The session is stored in a module-level dictionary
(`ml_models`) and accessed from endpoint handlers.

Loading at startup rather than on first request means:
- The first request is not penalised by model load time
- The application is not ready to serve until the model is loaded
- In Kubernetes, the readiness probe will not pass until startup completes,
  naturally preventing traffic from reaching an unready pod

On first run the model weights are downloaded from HuggingFace (~200MB). On subsequent
runs they are loaded from the local cache (`~/.cache/huggingface`). In Docker this
cache path should be mounted as a named volume to avoid re-downloading on every
container restart.

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
operational and models are loaded.

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

---

## Request models

Two separate Pydantic models are defined rather than reusing one model for all
endpoints. This makes the contract explicit per endpoint and prevents the gateway
from accidentally sending format fields to background removal requests or vice versa.

`ProcessRequest` — used by `/convert_format`. Fields: `source_sk`, `input_format`,
`target_sk`, `output_format`.

`BackgroundRemovalRequest` — used by `/remove_background`. Fields: `source_sk`,
`target_sk` only.

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
being re-raised. `FfmpegError` (or plain `Exception` when ffmpeg is mocked) is
caught in `convert_format` and logged with stderr output. `FileNotFoundError` and
general `Exception` are caught in `remove_background` with appropriate log messages.

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

**Synchronous handlers** — both endpoint functions are plain `def`, not `async def`.
FFmpeg invocation and file I/O are blocking operations that would block the event loop
if run in an `async` function. FastAPI automatically runs plain `def` functions in a
thread pool executor, keeping the event loop free for other requests.

**CPU-only inference** — the rembg model runs on CPU. No GPU is required or configured.
This keeps the Docker image and Kubernetes pod spec simple and avoids GPU quota
constraints on cloud providers.

**Return original key not resolved path** — endpoints return `request.source_sk` /
`request.target_sk` (the original UUID key) rather than the full resolved filesystem
path from `safe_path()`. The orchestrator already knows the storage key and uses it
to update the database; returning the full path would leak internal filesystem structure.

---

## Dependency management

Production and development dependencies are separated using uv dependency groups:

```
[project.dependencies]       <- installed in production (Docker)
fastapi, uvicorn, ffmpeg-python, rembg, python-dotenv

[dependency-groups.dev]      <- only installed locally and in CI
pytest, httpx
```

The Docker image uses `uv sync --no-dev` to exclude test dependencies from the
production image, reducing image size and attack surface.

---

## Testing

Unit and integration tests use FastAPI's `TestClient` backed by `httpx`. The rembg
model session is replaced with a `MagicMock` via `monkeypatch.setitem` before every
test, preventing any model download or load during the test suite. The storage
directory is redirected to a `tmp_path` fixture directory via `monkeypatch.setenv`,
isolating test file I/O from the real storage path.

Test coverage includes health check responses, `safe_path` traversal protection,
successful format conversion and background removal, FFmpeg and model failures
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

| Variable          | Default                        | Purpose                                |
|-------------------|--------------------------------|----------------------------------------|
| `WORKER_PORT`     | `8082`                         | Port the uvicorn server binds to       |
| `STORAGE_DATA_DIR`| `/tmp/imageprocessing/data`    | Shared file storage base path          |
| `WORKER_URL`      | `http://localhost:8082`        | Configured in orchestrator, not worker |

The health endpoint confirms the service is running and the model is loaded:

```
curl http://localhost:8082/health
```

To run the test suite:

```
uv run pytest test/ -v
```

---
