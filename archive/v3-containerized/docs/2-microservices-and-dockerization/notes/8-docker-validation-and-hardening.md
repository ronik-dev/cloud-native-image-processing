# Architecture Hardening & Automated Validation

> This document details the security hardening applied to the Cloud-Native Image Processing microservices and the End-to-End (E2E) testing suite used to validate the container orchestration.

---

### 1. Security Hardening: The Non-Root Standard

By default, Docker containers run as `root`, which poses a severe security risk in cloud environments. To adhere to the **Principle of Least Privilege**, all application containers have been hardened to run as an unprivileged user (`appuser`).

#### The Volume Mount Trap
When shifting to a non-root user, Docker's volume mounting behavior introduces a critical edge case: if a volume is mapped to a directory that does not exist inside the container, Docker daemon creates the directory as `root`. 

To prevent `Permission Denied` errors when the Python Machine Learning libraries attempt to download multi-hundred-megabyte weights, all cache directories are explicitly pre-created and chowned during the build phase.

**Implementation (Python Worker Example):**
```dockerfile
# 1. Create the non-root user and group with a dedicated home directory
RUN addgroup --system appgroup && adduser --system --group --home /home/appuser appuser

# 2. Pre-create ALL shared data and cache directories
RUN mkdir -p /tmp/imageprocessing/data \
             /home/appuser/.cache/huggingface \
             /home/appuser/.u2net

# 3. Grant full ownership of the app and all data/cache folders to appuser
RUN chown -R appuser:appgroup /app \
                              /tmp/imageprocessing/data \
                              /home/appuser

# 4. Strip privileges and switch to the non-root user
USER appuser
```

---

### 2. Orchestration & Startup Synchronization

Heavy machine learning containers (like the FastAPI worker) require significant time to boot and load `.onnx` models into RAM. To prevent Gateway and Orchestrator race conditions, the cluster utilizes native Docker healthchecks.

**Implementation (`docker-compose.yml`):**
```yaml
  worker:
    # ...
    healthcheck:
      # Requires installing `curl` via apt-get in the Python slim image
      test: ["CMD", "curl", "-f", "http://localhost:8082/health"]
      interval: 15s 
      timeout: 5s
      retries: 10 
```
The Java Orchestrator is configured with `depends_on: worker: condition: service_healthy`, ensuring it waits patiently until the ML models are fully loaded and the `200 OK` health status is achieved.

---

### 3. Automated End-to-End (E2E) Validation

To validate the integration of the Gateway, Orchestrator, Database, and Worker, the architecture utilizes a stateful Python `pytest` suite. This suite simulates a real frontend user journey, verifying HTTP status codes, JSON payload mapping, and concurrent asynchronous processing.

#### Setup Requirements
The testing suite runs on the host machine (not inside a container) and requires an isolated virtual environment.

```bash
# Initialize and activate the virtual environment
uv venv .venv
source .venv/bin/activate

# Install testing dependencies
uv pip install pytest requests
```

#### The Testing Strategy
The test suite (`test_e2e.py`) sequentially executes the following operations:
1. **State Injection:** Creates a User and an associated Image, passing the IDs downstream dynamically.
2. **Edge-Case Safety:** Uploads a mathematically valid 1x1 transparent Base64 PNG to prevent EXIF-parsing crashes in the PIL/Pillow libraries.
3. **Concurrency:** Submits three different processing jobs (Format Conversion, Background Removal, Object Detection).
4. **Asynchronous Polling:** Utilizes `concurrent.futures.ThreadPoolExecutor` to poll the Orchestrator's `202 Accepted` status endpoint until the jobs return a `DONE` status.
5. **Data Immutability:** Deletes the user at the end of the test, triggering a cascade delete in PostgreSQL to leave the database completely clean.

**Execution:**
```bash
# Run the test suite with verbose output and live standard-out printing
pytest test_e2e.py -v -s
```
