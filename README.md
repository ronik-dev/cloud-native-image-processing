# Cloud-Native Image Processing Platform

> Thesis project - Bachelor of Science in Computer Science Engineering, SUPSI DTI/ISIN

A cloud-native platform for image processing and AI-driven analysis, built as a
progressive case study covering the full DevOps lifecycle: from a Spring Boot
monolith to a microservices architecture deployed on Kubernetes with a service mesh.

---

## What it does

Users upload images through a web dashboard and trigger three categories of processing:

- **Format conversion** — convert between image formats (JPG, PNG, GIF, WebP, BMP) via FFmpeg
- **Background removal** — AI-driven background removal using the `isnet-general-use` model via rembg
- **Object detection** — detect and annotate objects with bounding boxes using Deformable DETR (SenseTime, COCO 2017)

The focus of the project is not the processing logic itself, but the **architectural
orchestration** around it: service decomposition, container lifecycle management,
CI/CD automation, authentication, and observability.

---

## Architecture overview

The system evolves progressively across six sprints:

```
Sprint 1  Spring Boot monolith        --> single JVM, Spring MVC, REST API
Sprint 2  Microservices               --> Gateway / Orchestrator / Python Worker
Sprint 3  Containerisation            --> Docker, docker-compose
Sprint 4  Kubernetes                  --> Minikube, HPA, Ingress, PersistentVolumes
Sprint 5  CI/CD + Security            --> GitLab CI/CD pipeline, Keycloak, JWT
Sprint 6  Service Mesh                --> Istio, mTLS, Prometheus, Grafana, Kiali, Jaeger
```

### Services (Sprint 2 onwards)

| Service | Language | Port | Responsibility |
|---|---|---|---|
| Gateway | Java / Spring Boot | 8080 | Public API (`/api/*`), UI serving, error forwarding, BFF pattern |
| Orchestrator | Java / Spring Boot | 8081 | Domain model, job lifecycle, storage, worker dispatch |
| AI Worker | Python / FastAPI | 8082 | FFmpeg processing, rembg inference, Deformable DETR inference |
| PostgreSQL | — | 5432 | Persistence, owned exclusively by orchestrator |

### Traffic flow

```
Browser
  |  HTTP :8080
  v
Gateway (/api/*)
  |  HTTP :8081/internal
  v
Orchestrator --> shared storage directory
  |  HTTP :8082
  v
AI Worker --> shared storage directory
```

### Domain model

```
User --< Image --< ProcessingJob
```

- `User` owns many `Image` records
- Each `Image` can have many `ProcessingJob` records (one per processing request)
- `ProcessingJob` tracks type (`FORMAT_CONVERSION`, `BACKGROUND_REMOVAL`, `OBJECT_DETECTION`),
  status (`PENDING` -> `RUNNING` -> `DONE` / `FAILED`), and output storage key

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend (Java) | Java 21, Spring Boot 4.0.6, Spring Data JPA, Spring MVC, Spring WebFlux (WebClient) |
| Backend (Python) | Python 3.13, FastAPI, Uvicorn, uv |
| Processing | FFmpeg, ffmpeg-python, rembg, Deformable DETR (HuggingFace transformers), Pillow |
| ML runtime | PyTorch CPU, torchvision |
| Containerisation | Docker, Docker Compose (Sprint 3) |
| Orchestration | Kubernetes (Minikube -> GKE), Helm (Sprint 4+) |
| CI/CD | GitLab CI/CD (Sprint 5) |
| Auth | Keycloak, OAuth 2.0, JWT (Sprint 5) |
| Service mesh | Istio, Envoy (Sprint 6) |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Kiali, Jaeger |

---

## Getting started

### Sprint 1 — Monolith

#### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 18
- FFmpeg

> These instructions are written for **Arch Linux**. The same tools apply on other
> operating systems but installation commands will differ.

**Install dependencies**

```bash
sudo pacman -S jdk21-openjdk maven postgresql ffmpeg
```

**Configure the database**

```bash
sudo -u postgres initdb -D /var/lib/postgres/data
sudo systemctl enable --now postgresql
sudo -i -u postgres psql
```

```sql
CREATE USER my_app_user WITH PASSWORD 'my_secure_password';
CREATE DATABASE cloud_native_db OWNER my_app_user;
```

### 3. Configure the application

Create an `application-local.properties` file at `src/main/resources/`
(this file is git-ignored and must never be committed):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/cloud_native_db
spring.datasource.username=my_app_user
spring.datasource.password=my_secure_password
```

Optionally, override the storage directory for uploaded and processed files
(defaults to `/tmp/imageprocessing/data`):

```properties
storage.data-dir=/your/preferred/path
```

The `local` profile is already set as the active profile in `application.properties`,
so no extra flags are needed at runtime.

### 4. Run the application

```bash
mvn clean spring-boot:run
```

Dashboard at `http://localhost:8080`, API at `http://localhost:8080/api`.

---

### Sprint 2 — Microservices

#### Additional prerequisites

- Python 3.13
- uv (`sudo pacman -S uv`)

#### Install Python worker dependencies

```bash
cd worker
uv sync
```

On first startup the worker downloads model weights from HuggingFace (~364MB total).
These are cached in `~/.cache/huggingface` and not re-downloaded on subsequent runs.

#### Run all services

Each service requires its own terminal:

| Service | Command | Port |
|---|---|---|
| PostgreSQL | `sudo systemctl start postgresql` | 5432 |
| Orchestrator | `mvn spring-boot:run -pl orchestrator` | 8081 |
| Gateway | `mvn spring-boot:run -pl gateway` | 8080 |
| Worker | `cd worker && uv run python main.py` | 8082 |

Dashboard at `http://localhost:8080`.

#### Environment variables

| Variable | Service | Default | Purpose |
|---|---|---|---|
| `ORCHESTRATOR_URL` | Gateway | `http://localhost:8081/internal` | Orchestrator base URL |
| `WORKER_URL` | Orchestrator | `http://localhost:8082` | Worker base URL |
| `STORAGE_DATA_DIR` | Orchestrator + Worker | `/tmp/imageprocessing/data` | Shared file storage |
| `DETECTION_THRESHOLD` | Worker | `0.5` | Min confidence for object detection |

#### Build commands

```bash
# Build common module and install to local Maven repository
mvn clean install -pl common --also-make

# Build all Java modules
mvn clean package

# Run worker tests
cd worker && uv run pytest test/ -v
```

---

## API quick reference

All endpoints are served by the gateway at `http://localhost:8080`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/users` | Register a user |
| `GET` | `/api/users` | List all users |
| `DELETE` | `/api/users/{id}` | Delete a user |
| `POST` | `/api/images` | Upload an image (`multipart/form-data`) |
| `GET` | `/api/images/{id}` | Get image metadata |
| `DELETE` | `/api/images/{id}` | Delete an image |
| `GET` | `/api/users/{id}/images` | List images for a user |
| `POST` | `/api/images/{id}/jobs` | Create a processing job |
| `GET` | `/api/images/{id}/jobs` | List jobs for an image |
| `POST` | `/api/jobs/{id}/process` | Trigger async execution |
| `GET` | `/api/jobs/{id}` | Poll job status |
| `GET` | `/api/jobs/{id}/result` | Download the processed result |
| `DELETE` | `/api/jobs/{id}` | Delete a job |

Supported job types: `FORMAT_CONVERSION`, `BACKGROUND_REMOVAL`, `OBJECT_DETECTION`.

Full API and architecture documentation is in `docs/`.

---

## Project structure

```
cloud-native-image-processing/
├── pom.xml                  <- parent Maven POM
├── common/                  <- shared DTOs, enums, exceptions (plain JAR, no Spring)
├── orchestrator/            <- domain service (Spring Boot)
├── gateway/                 <- BFF service (Spring Boot)
├── worker/                  <- AI worker (Python / FastAPI)
├── docs/
│   ├── 1-monolith/
│   └── 2-microservices-and-dockerization/
│       ├── notes/
│       └── sprint-2-microservices-recap.md
└── README.md
```

---

## Development workflow

See the [Contributing Guide](https://gitlab-edu.supsi.ch/dti-isin/roberto.guidi/didattica/progetti-semestre-diploma/cloud-native-image-processing/-/wikis/Contributing-Guide) for the full workflow,
label taxonomy, branch naming convention, and Definition of Done.

```bash
# 1. Pick an issue from the board and move it to In Progress
# 2. Create a branch
git checkout -b feature/26-python-worker

# 3. Implement, commit
git commit -m "feat(#26): add /detect_objects endpoint"

# 4. Open an MR targeting dev (use the MR template)
# 5. MR merged -> issue closed automatically via 'Closes #26'
```

Branch naming: `type/issue-id-short-description`
Target branch for MRs: always `dev` — never `main` directly.

---

## Sprint roadmap

| Sprint | Milestone | Status |
|---|---|---|
| 1 | Spring Boot monolith | Complete |
| 2.1 | Microservices | Complete |
| 2.2 | Containerisation (Docker + docker-compose) | Planned |
| 3 | Kubernetes | Planned |
| 4 | CI/CD + Security | Planned |
| 5 | Service Mesh | Planned |
| 6 | Helm + Cloud + Polish| Planned |

---

## Author

**Nicola Romano** — nicola.romano@student.supsi.ch
Supervisors: Massimo Coluzzi, Roberto Guidi
SUPSI — DTI / ISIN, June 2026
