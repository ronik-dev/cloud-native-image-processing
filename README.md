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
Sprint 1  Spring Boot monolith        --> single JVM, Spring Data REST, HAL API
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
| Image Service | Java / Spring Boot | Domain model, Spring Data REST API, job coordination |
| Python Worker | Python (FastAPI) | FFmpeg processing, HuggingFace inference |
| PostgreSQL | - | Persistence for all domain entities |
| Keycloak | - | OAuth 2.0 identity provider (from Sprint 4) |

### Domain model

```
User --< Image --< ProcessingJob
```

- `User` owns many `Image` records
- Each `Image` can have many `ProcessingJob` records (one per processing request)
- `ProcessingJob` tracks type (`FORMAT_CONVERSION`, `THUMBNAIL`, `AI_CLASSIFICATION`),
  status (`PENDING` --> `RUNNING` --> `DONE` / `FAILED`), and output file path

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Data JPA, Spring Data REST, Spring Security |
| Processing | FFmpeg, Hugging Face Transformers (Python) |
| Containerisation | Docker, Docker Compose |
| Orchestration | Kubernetes (Minikube --> GKE), Helm |
| CI/CD | GitLab CI/CD |
| Auth | Keycloak, OAuth 2.0, JWT |
| Service mesh | Istio, Envoy |
| Observability | Prometheus, Grafana, Kiali, Jaeger |
| IaC (bonus) | Terraform, Ansible |

---

## Getting started (Sprint 1 - monolith)

### Prerequisites
- Java 21
- Maven 3.9+
- PostgreSQL

### Configure the application

> ...work in progress

### Run the application

> ...work in progress

---

## Project structure

```
cloud-native-image-processing/
`-- .gitlab/
    |-- issue_templates/
    |   |-- user_story.md
    |   `-- task.md
    `-- merge_request_templates/
        `-- default.md
```
---

## Development workflow

See the [Contributing Guide](../../wikis/Contributing-Guide) for the full workflow,
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
| 1 | Spring Boot monolith | In progress |
| 2 | Microservices + Docker |  Planned |
| 3 | Kubernetes | Planned |
| 4 | CI/CD + Security | Planned |
| 5 | Service Mesh | Planned |
| 6 | Helm + Cloud + Polish | Planned |

---

## Author

**Nicola Romano** - nicola.romano@student.supsi.ch
Supervisors: Massimo Coluzzi, Roberto Guidi
SUPSI - DTI / ISIN, May 2026
