# 1 CI/CD Pipeline: Testing & Kaniko Build (Tasks 56, 57, 58, 79, 59)
> This guide covers the initial implementation of the `.gitlab-ci.yml` pipeline, focusing on the `test` and `build` stages.

### Context
To establish a robust Continuous Integration (CI) environment, the project requires an automated pipeline to run unit tests, integration tests, and package the microservices into OCI-compliant container images. The pipeline is executed on GitLab's shared Docker runners.

### 1. Test Stage: Java and Python
The pipeline is split into technology-specific test jobs to maximize caching and minimize execution time.

**Java Tests (`java-tests`):**
* Runs `mvn test` using `maven:3.9.16-eclipse-temurin-21-alpine`.
* The `.m2/repository` is cached locally to prevent downloading the internet on every run.
* A custom regular expression (`/(\d+\.\d+)% covered/`) parses the JaCoCo test coverage output directly into GitLab's UI. 

**Python Tests (`python-tests`):**
* Uses `python:3.13-slim` and the `uv` package manager.
* Tests run in total isolation. Because heavy machine learning models (rembg, DETR) and FFmpeg subprocesses are fully mocked using `monkeypatch.setitem` and `MagicMock`[cite: 2], the system doesn't require downloading the ~400MB models during CI.

**Integration Tests (`integration-tests`):**
* Designed to run Testcontainers (spinning up a real PostgreSQL database) for the Orchestrator.
* **Blocker:** The current SUPSI GitLab runners do not expose `/var/run/docker.sock` (Docker-in-Docker is disabled). This job currently fails with "Could not find a valid Docker environment."
* **Resolution:** Marked with `allow_failure: true` to prevent it from blocking the pipeline while runner access is evaluated by the infrastructure team.

### 2. Build Stage: Daemonless Image Building with Kaniko
Because the GitLab runners do not expose the Docker socket, standard `docker build` and `docker push` commands cannot be used. 

**Decision: Kaniko**
Google's Kaniko (`gcr.io/kaniko-project/executor:debug`) was introduced to build and push OCI images entirely in userspace. It requires no Docker daemon privileges.

**Matrix Build:**
A `parallel: matrix` strategy is used to dynamically generate three separate build jobs from a single template, targeting the three services:
1. `gateway` (pushed as `frontend`)
2. `orchestrator`
3. `worker`

**Security & TLS Workaround:**
The SUPSI institutional GitLab registry uses an internal/self-signed certificate that Kaniko does not trust by default (`x509: certificate signed by unknown authority`). To bypass this purely for the registry push, the `--skip-tls-verify-registry="${CI_REGISTRY}"` flag was explicitly added to the Kaniko executor command.

### 3. Tagging Strategy & Branch Name Sanitization
The pipeline pushes two tags per image:
1. The immutable short commit SHA (for precise traceability).
2. A floating tag representing the branch.

**The Slug Fix:**
When pushing from feature branches like `feature/59-build-and-push-images`, Kaniko initially crashed because OCI image tags do not allow forward slashes (`/`). This was resolved by using GitLab's built-in `$CI_COMMIT_REF_SLUG` variable, which automatically converts slashes to dashes (e.g., `feature-59-build-and-push-images`), creating a universally safe image tag.
