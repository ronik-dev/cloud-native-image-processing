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
Sprint 2  Microservices & Containers  --> Gateway / Orchestrator / Python Worker, Docker, docker-compose
Sprint 3  Kubernetes                  --> Minikube, Deployments/StatefulSets, Ingress, Kafka job queue, KEDA
Sprint 4  CI/CD + Security            --> GitLab CI/CD pipeline, Kaniko, Keycloak, Session Cookies, BFF pattern
Sprint 5  Service Mesh                --> Istio, mTLS, Prometheus, Grafana, Kiali, Jaeger
Sprint 6  Helm + Cloud + Polish       --> GKE/AKS/EKS, Helm charts
```

### Services (Sprint 4 onwards)

| Service | Language | Port | Responsibility |
|---|---|---|---|
| Gateway | Java / Spring Boot | 8080 | Public API (`/api/*`), UI serving, error forwarding, BFF OAuth2 Client |
| Orchestrator | Java / Spring Boot | 8081 | Domain model, job lifecycle, storage, worker dispatch |
| AI Worker | Python / FastAPI | 8082 | FFmpeg processing, rembg inference, Deformable DETR inference |
| PostgreSQL | — | 5432 | Persistence, owned exclusively by orchestrator and Keycloak |
| Keycloak | Java | 8080 | Identity Provider (IdP), OIDC authentication, user management |

### Traffic flow

```
Browser
  |  1. HTTP GET /api/me (No session)
  v
Gateway (BFF) -. 2. 302 Redirect .-> Keycloak (IdP)
  |  3. Authenticate & Swap Code for Token
  v
Orchestrator (Internal API) --> shared storage directory
  |  4. Publish JobRequestMessage (Kafka)
  v
AI Worker --> shared storage directory
```

### Domain model

```
User --< Image --< ProcessingJob
```

- `User` owns many `Image` records (Bridged locally upon Keycloak login)
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
| Containerisation | Docker, Docker Compose (Sprint 2) |
| Orchestration | Kubernetes (Minikube -> GKE), Helm (Sprint 6) |
| Messaging | Apache Kafka, single-broker KRaft mode (Sprint 3) |
| Autoscaling | KEDA, Kafka-lag-driven `ScaledObject` on the Worker (Sprint 3) |
| Load testing | k6, sustained-load HTTP script against the full API (Sprint 3) |
| CI/CD | GitLab CI/CD, Kaniko (daemonless builds) (Sprint 4) |
| Auth | Keycloak, OAuth 2.0, OIDC, Session Cookies, BFF pattern (Sprint 4) |
| Service mesh | Istio, Envoy (Sprint 5) |
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

### Sprint 3 — Kubernetes

#### Additional prerequisites

- `kubectl` v1.35+
- `minikube` v1.38+
- Helm (for KEDA, see below)

#### Full deployment

The complete, current architecture (namespace, ConfigMaps/Secrets, PVCs, Deployments/StatefulSets, Kafka, KEDA, Ingress) is documented end-to-end — including the full dependency-ordered `kubectl apply` sequence — in [`docs/3-kubernetes/sprint-3-recap.md`](docs/3-kubernetes/sprint-3-recap.md).

```bash
minikube start
eval $(minikube docker-env)   # target Minikube's own Docker daemon, per-terminal
docker compose build
minikube addons enable ingress
kubectl apply -R -f k8s/
```

Once every workload is `Running`/`Ready`, add `api.imageprocessing.local` to `/etc/hosts` pointing at the Ingress and run `minikube tunnel` (kept open in its own terminal) to reach the dashboard at `http://api.imageprocessing.local`.

---

### Sprint 4 — CI/CD & Security

Sprint 4 transitions the API Gateway into a Backend-For-Frontend (BFF) OIDC Client and delegates all authentication and identity management to a locally deployed Keycloak instance. It also introduces a fully automated GitLab CI/CD pipeline using daemonless Kaniko builds.

#### CI/CD Pipeline
- Testing: Automated Java (JUnit + JaCoCo) and Python (pytest + pytest-cov) execution.
- Build: Kaniko dynamically builds and pushes images to a private registry without requiring Docker-in-Docker.
- Deploy: Deployments pull via `imagePullSecrets` and are explicitly rolled out via a custom project runner.

#### Authentication & Initial Setup
With Keycloak deployed, unauthenticated requests are explicitly blocked.

1. **Bootstrap Keycloak:** Keycloak is deployed with an init Job that automatically creates its PostgreSQL database. A ConfigMap auto-imports the `imageprocessing` realm and `gateway` client.
2. **Access the Admin Console:** Navigate to `http://keycloak.imageprocessing.local`. Use the credentials defined in `k8s/keycloak/secret.yml.example` (or your applied secret) to access the Master realm.
3. **Create a User:** 
   - Switch to the `imageprocessing` realm.
   - Create a new user. **Important:** The Orchestrator requires all users to have a valid email address.
   - Set a permanent password (disable the "Temporary" toggle).
4. **Log In:** Navigate to `http://api.imageprocessing.local`. The Gateway will intercept the request and redirect you to Keycloak to securely log in. Session cookies are maintained server-side (BFF pattern) to ensure the static frontend does not handle raw JWTs.

---

## API quick reference

Because the Gateway acts as a Backend-For-Frontend (BFF), direct user management routes (`/api/users`) have been secured and refactored. The frontend relies on session cookies and the `/api/me` namespace.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/me` | Retrieve the authenticated user's profile |
| `GET` | `/api/me/images` | List images owned by the authenticated user |
| `DELETE` | `/api/me` | Delete the authenticated user and cascade-delete their assets |
| `POST` | `/api/images` | Upload an image (`multipart/form-data`) |
| `GET` | `/api/images/{id}` | Get image metadata |
| `DELETE` | `/api/images/{id}` | Delete an image |
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
├── .gitlab-ci.yml           <- automated testing, Kaniko build, and deploy pipeline
├── pom.xml                  <- parent Maven POM
├── common/                  <- shared DTOs, enums, exceptions (plain JAR, no Spring)
├── orchestrator/            <- domain service (Spring Boot)
├── gateway/                 <- BFF service (Spring Boot, OAuth2 Client)
├── worker/                  <- AI worker (Python / FastAPI)
├── k8s/                     <- Kubernetes manifests (namespace, ConfigMaps/Secrets, PV/PVC,
│                                Deployments/StatefulSets, Services, Ingress, Kafka, KEDA, Keycloak)
├── test/
│   └── loadtest.js          <- k6 sustained-load script against the deployed API
├── docs/
│   ├── 1-monolith/
│   ├── 2-microservices-and-dockerization/
│   ├── 3-kubernetes/
│   ├── 4-ci-cd-and-security/
│   │   ├── notes/           <- pipeline and keycloak integration dev logs
│   │   ├── sprint-4-part-1-cicd-recap.md
│   │   └── sprint-4-part-2-security-recap.md
│   └── README.md
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
| 2 | Microservices & Containerisation| Complete |
| 3 | Kubernetes (Deployments/StatefulSets, Ingress, Kafka, KEDA/HPA) | Complete |
| 4 | CI/CD + Security (Keycloak, BFF) | Complete |
| 5 | Service Mesh | Planned |
| 6 | Helm + Cloud + Polish| Planned |

---

## Author

**Nicola Romano** — nicola.romano@student.supsi.ch
Supervisors: Massimo Coluzzi, Roberto Guidi
SUPSI — DTI / ISIN, June 2026
