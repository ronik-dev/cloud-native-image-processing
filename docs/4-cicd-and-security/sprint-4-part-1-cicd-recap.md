# Sprint 4 (Part 1) : CI/CD 

> This document consolidates the implementation of the Continuous Integration and Continuous Deployment (CI/CD) pipelines into a single reference guide.

## Table of Contents
[[ _TOC_ ]]

## 1. Architecture Overview
Sprint 4 bridges the gap between raw source code and the Kubernetes architecture built in Sprint 3. The process is fully automated via GitLab CI/CD: code pushed to the repository is tested, built via a daemonless executor, pushed to a private registry, and seamlessly rolled out to the Minikube cluster.

## 2. Continuous Integration (Tests)
The `test` stage ensures code quality before any image is built.
* **`java-tests`**: Leverages Maven with a cached `.m2` repository to execute the JUnit 5 test suite for `orchestrator` and `gateway`. JaCoCo aggregates coverage metrics, parsed automatically by GitLab using a custom regex.
* **`python-tests`**: Executes `pytest` against the FastAPI `worker`. It runs in total isolation, bypassing model downloads via HuggingFace and rembg `MagicMock` patching.
* **`integration-tests`**: Intended for Testcontainers (database integration), this job is currently set to `allow_failure: true` due to the lack of privileged Docker socket access on the institutional GitLab runners.

## 3. Continuous Delivery (Kaniko Image Building)
Because standard `docker build` commands are blocked by the runner's lack of Docker-in-Docker support, **Kaniko** is used to compile the OCI images in userspace.

### 3.1 Matrix Build
A `parallel: matrix` strategy dynamically fans out into three parallel build jobs (`gateway`, `orchestrator`, `worker`), vastly reducing pipeline duration.

### 3.2 Floating Tags and Security
* **Tag Sanitization:** Branches like `feature/59-build-and-push-images` contain illegal characters for OCI tags. The pipeline uses `$CI_COMMIT_REF_SLUG` to format tags cleanly.
* **Institutional TLS:** To bypass certificate authority rejections from the internal `gitlab-edu.supsi.ch` registry, Kaniko is explicitly passed the `--skip-tls-verify-registry` argument.

## 4. Continuous Deployment (Kubernetes)
The Kubernetes cluster has been transitioned from local, statically-built images to a dynamic architecture which allows pull from the gitlab container registry.

### 4.1 Registry Authentication
A GitLab Deploy Token (`read_registry` scope) handles authentication. It is mounted into the `imageprocessing` namespace as a `docker-registry` secret (`gitlab-registry-secret`).

### 4.2 Manifest Updates
All custom deployments (`frontend`, `orchestrator`, `worker`) were updated to support CD:
* **`imagePullPolicy: Always`**: Replaces the `Never` policy. Forces the kubelet to actively fetch the newest image digest matching the floating branch tag.
* **`imagePullSecrets`**: Injected into the Pod `spec` to provide the Gitlab registry credentials.

## 5. Sprint Completion Status (Part 1)

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Pipeline Setup (#56) | Base `.gitlab-ci.yml` functional on runners | Done | Shell runner discarded for Docker executor |
| Java Tests (#57) | `mvn test` executed, JaCoCo coverage parsed | Done | |
| Python Tests (#58) | `pytest` executed, XML junit artifacts saved | Done | Fast execution via `uv` cache and Mocking |
| Integration Tests (#79) | Testcontainers execution | **Blocked** | Fails due to lack of DIND socket. Set to `allow_failure: true` |
| Image Builds (#59) | Kaniko builds and pushes 3 OCI images | Done | `$CI_COMMIT_REF_SLUG` used to fix slash issues in branch names |
| CD Kubernetes (#80) | Cluster pulls registry images | Done | `gitlab-registry-secret` added; `imagePullPolicy: Always` enforced |
