# Cloud-Native Image Processing Platform

> Thesis project - Bachelor of Science in Computer Science Engineering, SUPSI DTI/ISIN

A cloud-native platform for image processing and AI-driven analysis, built as a
progressive case study covering the full DevOps lifecycle: from a Spring Boot
monolith to a microservices architecture deployed on Kubernetes with a service mesh.

---

## What it does

Users upload images through a web dashboard and trigger two categories of processing:

- **Deterministic processing** - format conversion, thumbnail generation (FFmpeg)
- **AI-driven analysis** - image classification, background removal (Hugging Face pre-trained models)

The focus of the project is not the processing logic itself, but the **architectural
orchestration** around it: service decomposition, container lifecycle management,
CI/CD automation, authentication, and observability.

---

## Architecture overview

The system evolves progressively across six sprints:

```
Sprint 1  Spring Boot monolith        --> single JVM, Spring MVC, REST API
Sprint 2  Microservices + Docker      --> Gateway / Image Service / Python Worker
Sprint 3  Kubernetes                  --> Minikube, HPA, Ingress, PersistentVolumes
Sprint 4  CI/CD + Security            --> GitLab CI/CD pipeline, Keycloak, JWT
Sprint 5  Service Mesh                --> Istio, mTLS, Prometheus, Grafana, Kiali, Jaeger
Sprint 6  Cloud + Helm                --> GKE, Helm charts, multi-environment deploy
```

### Services (from Sprint 2 onwards)

| Service | Language | Responsibility |
|---|---|---|
| Gateway Service | Java / Spring Boot | UI, routing, authentication |
| Image Service | Java / Spring Boot | Domain model, REST API, job coordination |
| Python Worker | Python (FastAPI) | FFmpeg processing, HuggingFace inference |
| PostgreSQL | - | Persistence for all domain entities |
| Keycloak | - | OAuth 2.0 identity provider (from Sprint 4) |

### Domain model

```
User --< Image --< ProcessingJob
```

- `User` owns many `Image` records
- Each `Image` can have many `ProcessingJob` records (one per processing request)
- `ProcessingJob` tracks type (`FORMAT_CONVERSION`, `BACKGROUND_REMOVAL`, `AI_CLASSIFICATION`),
  status (`PENDING` --> `RUNNING` --> `DONE` / `FAILED`), and output storage key

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.0.6, Spring Data JPA, Spring MVC, Spring Validation |
| Processing | FFmpeg, Hugging Face Transformers (Python) |
| Containerisation | Docker, Docker Compose |
| Orchestration | Kubernetes (Minikube --> GKE), Helm |
| CI/CD | GitLab CI/CD |
| Auth | Keycloak, OAuth 2.0, JWT |
| Service mesh | Istio, Envoy |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Kiali, Jaeger |
| IaC (bonus) | Terraform, Ansible |

---

## Getting started (Sprint 1 - monolith)

### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 18
- FFmpeg

> These instructions are written for **Arch Linux**. The same tools apply on other
> operating systems but installation commands will differ.

### 1. Install dependencies

**Java 21**
```bash
sudo pacman -S jdk21-openjdk
java -version
```

**Maven**
```bash
sudo pacman -S maven
mvn -version
```

**PostgreSQL**
```bash
sudo pacman -S postgresql

# Initialise the data directory
sudo mkdir -p /var/lib/postgres
sudo chown -R postgres:postgres /var/lib/postgres
sudo -u postgres initdb -D /var/lib/postgres/data

# Enable and start the service
sudo systemctl enable --now postgresql
```

**FFmpeg**
```bash
sudo pacman -S ffmpeg
ffmpeg -version
```

### 2. Configure the database

Open a `psql` session and create the application user and database:

```bash
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

Hibernate creates or updates the schema automatically on startup (`ddl-auto=update`).

The dashboard is available at `http://localhost:8080` and the REST API at
`http://localhost:8080/api`.

### 5. API quick reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/users` | Register a user |
| `POST` | `/api/images?userId={id}` | Upload an image |
| `POST` | `/api/images/{id}/jobs` | Create a processing job |
| `POST` | `/api/jobs/{id}/process` | Trigger async execution |
| `GET` | `/api/jobs/{id}` | Poll job status |
| `GET` | `/api/jobs/{id}/result` | Download the result |

Full API documentation is in [`sprint1_architecture.md`](./sprint1_architecture.md).

---

## Project structure

```
cloud-native-image-processing/
|-- .gitlab/
|   |-- issue_templates/
|   `-- merge_request_templates/
|-- src/
|   `-- main/
|       `-- java/ch/supsi/imageprocessing/
|           |-- controller/
|           |-- service/
|           |-- processor/
|           |-- entity/
|           |-- repository/
|           |-- dto/
|           |-- exception/
|           `-- utils/
|-- pom.xml
`-- README.md
```

---

## Development workflow

See the [Contributing Guide](https://gitlab-edu.supsi.ch/dti-isin/roberto.guidi/didattica/progetti-semestre-diploma/cloud-native-image-processing/-/wikis/Contributing-Guide) for the full workflow,
label taxonomy, branch naming convention, and Definition of Done.

The short version:

```bash
# 1. Pick an issue from the board and move it to In Progress
# 2. Create a branch
git checkout -b feature/16-file-upload-endpoint

# 3. Implement, commit
git commit -m "feat(#16): add POST /upload endpoint"

# 4. Open an MR targeting dev (use the MR template)
# 5. MR merged --> issue closed automatically via 'Closes #16'
```

Branch naming: `type/issue-id-short-description`  
Target branch for MRs: always `dev` - never `main` directly.

---

## Sprint roadmap

| Sprint | Milestone | Status |
|---|---|---|
| 1 | Spring Boot monolith | Complete |
| 2 | Microservices + Docker | Planned |
| 3 | Kubernetes | Planned |
| 4 | CI/CD + Security | Planned |
| 5 | Service Mesh | Planned |
| 6 | Helm + Cloud + Polish | Planned |

---

## Author

**Nicola Romano** - nicola.romano@student.supsi.ch  
Supervisors: Massimo Coluzzi, Roberto Guidi  
SUPSI - DTI / ISIN, May 2026
