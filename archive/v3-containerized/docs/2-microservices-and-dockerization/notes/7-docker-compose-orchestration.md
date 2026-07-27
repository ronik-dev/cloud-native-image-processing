# Orchestrate Microservices: Docker Compose

> This guide outlines the local orchestration of the Cloud-Native Image Processing platform using Docker Compose. 
> These instructions assume all individual Dockerfiles (`gateway`, `orchestrator`, `worker`) have been created and you are executing commands from the root directory of the repository.

### 1. The Environment Vault (`.env`)
To adhere to cloud-native security principles, credentials and host-specific paths must never be hardcoded into the architecture files.

1. Create a `.env` file in the root of your project.
2. Ensure `.env` is added to your `.gitignore` file.
3. Populate it with your local configuration:
    ```env
    # Database Credentials
    POSTGRES_USER=my_app_user
    POSTGRES_PASSWORD=my_secure_password
    POSTGRES_DB=cloud_native_db

    # Shared Architecture Path (Host OS Directory)
    STORAGE_DATA_DIR=/tmp/imageprocessing/data
    ```

### 2. The Architecture Blueprint
The `docker-compose.yml` file acts as the single source of truth for the local cluster. It handles network isolation, volume mounting, configuration injection, and startup synchronization.

Create or update the `docker-compose.yml` in the root directory:

```yaml
services:
  gateway:
    container_name: gateway 
    build:
      context: .
      dockerfile: gateway/Dockerfile
    image: imageprocessing/gateway:latest
    # The Gateway acts as the sole entry point for the host machine
    ports:
      - "8080:8080"
    environment:
      - ORCHESTRATOR_URL=http://orchestrator:8081/internal
    depends_on:
      - orchestrator

  orchestrator:
    container_name: orchestrator 
    build:
      context: .
      dockerfile: orchestrator/Dockerfile
    image: imageprocessing/orchestrator:latest
    environment:
      - postgres_host=postgres
      - postgres_user_name=${POSTGRES_USER}
      - postgres_user_password=${POSTGRES_PASSWORD}
      - postgres_db_name=${POSTGRES_DB}
      - worker_url=http://worker:8082
      - STORAGE_DATA_DIR=${STORAGE_DATA_DIR}
    volumes:
      - data:${STORAGE_DATA_DIR}
    depends_on:
      postgres:
        condition: service_healthy
      worker:
        condition: service_started

  worker:
    container_name: worker 
    build:
      context: .
      dockerfile: worker/Dockerfile
    image: imageprocessing/worker:latest
    environment:
      - STORAGE_DATA_DIR=${STORAGE_DATA_DIR}
    volumes:
      - data:${STORAGE_DATA_DIR}
      # Caches map to the root directory prior to non-root security hardening
      - model_cache:/root/.cache/huggingface
      - rembg_cache:/root/.u2net

  postgres:
    container_name: postgres
    image: postgres:18-alpine
    environment:
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
      - POSTGRES_DB=${POSTGRES_DB}
    volumes:
      # Mounted one level up to support PostgreSQL 18+ volume structures
      - postgres_data:/var/lib/postgresql
    healthcheck: 
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  data:
    name: image_processing_data
  model_cache:
    name: ai-models-cache
  rembg_cache:
    name: rembg_cache
  postgres_data:
    name: postgres_data
```

---

### 3. Architectural Design Decisions

* **API Gateway Pattern:** Internal services (`orchestrator`, `worker`, `postgres`) do not have mapped `ports`. They are securely locked inside the Docker internal network. The Gateway (`8080`) is the only service exposed to the host system.
* **Startup Synchronization:** The Orchestrator will instantly crash if it attempts to connect to a database that is still booting. The Compose file implements a `healthcheck` that pings PostgreSQL using `pg_isready`. Docker delays the Orchestrator's startup until PostgreSQL is explicitly ready to accept queries.
* **Model Cache Persistence:** The Python worker relies on heavy ML models (HuggingFace Deformable DETR, U^2-Net). By mapping the `/root/.cache` and `/root/.u2net` paths to Docker volumes, the models are persisted on the host disk. This prevents the ~400MB models from being re-downloaded on every container restart.
* **PostgreSQL 18+ Mount Boundaries:** As of version 18, PostgreSQL images require the volume to be mounted at `/var/lib/postgresql` rather than `/var/lib/postgresql/data` to facilitate safe major-version database upgrades.

---

### 4. Cluster Execution and Maintenance

To boot the architecture in detached mode, pulling external images and building local images simultaneously:
```bash
docker compose up -d --build
```

To view unified logs for debugging race conditions or errors:
```bash
docker compose logs -f
```

To tear down the cluster and cleanly destroy the isolated network (this will *not* delete your cached data or database tables):
```bash
docker compose down
```

**Monitoring Disk Usage:**
To verify that the AI models are successfully writing to the host disk rather than inflating the ephemeral container layer:
```bash
docker system df
```
*Look for `Local Volumes` size to accurately reflect the ~400MB machine learning payloads.*
