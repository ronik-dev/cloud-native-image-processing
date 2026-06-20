# Containerize Python Service: AI Worker

> This guide is OS specific for Arch Linux, as this project is developed on this OS. 
> These instructions assume you have `docker` installed and the Docker daemon running (`sudo systemctl start docker`).
> All configuration edits assume the use of a standard text editor (like Neovim) in the terminal.

### 1. Multi-Stage Python Dockerfile

Unlike the Java services, Python is an interpreted language. However, a multi-stage build is still utilized to isolate the `uv` package manager and build cache from the final runtime image. 

We explicitly use a Debian-based `slim` image (`python:3.13-slim`) rather than Alpine. This is a critical architectural decision: Alpine uses `musl libc`, which is incompatible with the pre-compiled `glibc` wheels required by PyTorch and Torchvision. Using the Debian slim image allows these heavy ML dependencies to install instantly rather than compiling from source.

*(Note: Security hardening and non-root user creation will be addressed in a subsequent task. This baseline focuses on structural multi-stage isolation and system dependency wiring).*

1. Create `worker/Dockerfile`:
    ```dockerfile
    # Stage 1: Builder
    FROM python:3.13-slim AS builder

    # Install uv globally via pip (no system dependencies needed for this stage)
    RUN pip install uv

    WORKDIR /build

    # Copy dependency files to maximize Docker layer caching
    COPY worker/pyproject.toml .
    COPY worker/uv.lock .

    # Sync dependencies to create the isolated .venv
    RUN uv sync --no-dev

    # Stage 2: Runtime
    FROM python:3.13-slim AS runtime

    # Install required system binaries for ffmpeg-python and OpenCV
    RUN apt-get update && apt-get install -y \
        ffmpeg \
        && rm -rf /var/lib/apt/lists/*

    WORKDIR /app

    # Copy the pre-built virtual environment from the builder stage
    COPY --from=builder /build/.venv /app/.venv
    
    # Copy the Python source code
    COPY worker/src/ .

    EXPOSE 8082
    
    # Execute the application using the Python binary directly from the virtual environment
    ENTRYPOINT ["/app/.venv/bin/python", "main.py"]
    ```

### 2. Build and Test

Just like the Java services, the build must be executed from the root of the repository so the Docker context matches the `COPY worker/...` paths defined in the Dockerfile.

1. Build the image:
    ```Bash
    docker buildx build -f worker/Dockerfile -t imageprocessing/worker:dev .
    ```

2. Verify the image:
    ```Bash
    docker images
    ```
    Output should show your newly built worker image. Expect a significantly larger size than the Java services due to the ML libraries:
    ```Bash
    IMAGE                              ID             DISK USAGE   CONTENT SIZE   EXTRA
    imageprocessing/aiworker:dev       <..........>       2.86GB          670MB    U
    imageprocessing/gateway:dev        <..........>        349MB          108MB    
    imageprocessing/orchestrator:dev   <..........>        433MB          148MB    
    ```

3. Test execution locally:
    ```Bash
    docker run --network host imageprocessing/worker:dev
    ```
    Look for the successful FastAPI startup log:
    ```Bash
    INFO:     Started server process [1]
    INFO:     Waiting for application startup.
    INFO:     Application startup complete.
    INFO:     Uvicorn running on [http://0.0.0.0:8082](http://0.0.0.0:8082) (Press CTRL+C to quit)
    ```
    Press `Ctrl+C` to stop the container.
