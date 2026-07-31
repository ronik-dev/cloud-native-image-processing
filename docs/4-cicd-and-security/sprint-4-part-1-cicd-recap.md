# Sprint 4 (Part 1) : CI/CD 

> This document consolidates the implementation of the Continuous Integration and Continuous Deployment (CI/CD) pipelines into a single reference guide.

## Table of Contents
[[ _TOC_ ]]

## 1. Architecture Overview
Sprint 4 bridges the gap between raw source code and the Kubernetes architecture built in Sprint 3. The process is fully automated via GitLab CI/CD: code pushed to the repository is tested, built via a daemonless executor, pushed to a private registry, and rolled out to the Minikube cluster via a manually-triggered deploy job.

## 2. Continuous Integration (Tests)
The `test` stage ensures code quality before any image is built.
* **`java-tests`**: Leverages Maven with a cached `.m2` repository to execute the JUnit 5 test suite for `orchestrator` and `gateway`. JaCoCo aggregates coverage metrics, parsed automatically by GitLab using a custom regex.
* **`python-tests`**: Executes `pytest` against the FastAPI `worker`, with `pytest-cov` generating a Cobertura-format coverage report (`coverage_report` artifact) alongside the JUnit results. It runs in total isolation, bypassing model downloads via HuggingFace and rembg `MagicMock` patching.
* **`integration-tests`**: Intended for Testcontainers (database integration), this job is currently set to `allow_failure: true` due to the lack of privileged Docker socket access on the institutional GitLab runners.

## 3. Continuous Delivery (Kaniko Image Building)
Because standard `docker build` commands are blocked by the runner's lack of Docker-in-Docker support, **Kaniko** is used to compile the OCI images in userspace.

### 3.1 Matrix Build
A `parallel: matrix` strategy dynamically fans out into three parallel build jobs (`gateway` → `frontend`, `orchestrator`, `worker`), vastly reducing pipeline duration. `SERVICE` (the Dockerfile's directory) and `IMAGE_NAME` (the registry name) are tracked as separate matrix variables, since `gateway/`'s directory was never renamed even though its published image name was.

### 3.2 Floating Tags and Security
* **Tag Sanitization:** Branches like `feature/59-build-and-push-images` contain illegal characters for OCI tags. The pipeline uses `$CI_COMMIT_REF_SLUG` to format tags cleanly.
* **Branch-aware floating tags:** `main` publishes `:latest`; every other branch publishes `:$CI_COMMIT_REF_SLUG` (e.g. `:dev`). Deliberately not a single shared `:latest` across branches — that would let whichever branch's pipeline ran most recently silently overwrite the other's floating tag, which the deploy stage would then pull without warning.
* **Institutional TLS — deliberate tradeoff, not an oversight:** the internal `gitlab-edu.supsi.ch` registry serves a certificate Kaniko doesn't trust (signed by an internal/institutional CA it has no way to verify). Kaniko is passed `--skip-tls-verify-registry` to work around this. This is not equivalent to "trusting a custom CA" — it disables certificate verification for that registry entirely, meaning the push traffic has no protection against a MITM on the path to the registry. Judged an acceptable, proportionate risk for a semester project running entirely on institutional infrastructure; documented here explicitly so it reads as a conscious decision rather than an unexamined workaround.

## 4. Continuous Deployment (Kubernetes)
The Kubernetes cluster has been transitioned from local, statically-built images to a dynamic architecture which pulls from the GitLab Container Registry, with a CI-triggered rollout restart to actually apply new images.

### 4.1 Registry Authentication
A GitLab Deploy Token (`read_registry` scope) handles authentication. It is mounted into the `imageprocessing` namespace as a `docker-registry` secret (`gitlab-registry-secret`).

### 4.2 Manifest Updates
All custom deployments (`frontend`, `orchestrator`, `worker`) were updated to support CD:
* **`imagePullPolicy: Always`**: Replaces the `Never` policy. Forces the kubelet to actively fetch the newest image digest matching the floating branch tag.
* **`imagePullSecrets`**: Injected into the Pod `spec` to provide the GitLab registry credentials.

### 4.3 Deploy Mechanism — GitLab Agent for Kubernetes, Attempted and Blocked
The Minikube cluster runs on a personal laptop with no inbound network path from the institutional runner — a plain `kubectl apply` from a CI job on `gitlab-edu.supsi.ch`'s infrastructure has no way to reach it. A **push-based** fix (e.g. a webhook listener on the laptop) was rejected: it just relocates the same inbound-connectivity problem to a different protocol, and would require tunneling (ngrok, reverse SSH) to expose a port on a personal machine.

The correct **pull-based** answer is the **GitLab Agent for Kubernetes (KAS)**: an agent pod runs inside the target cluster and opens an *outbound* connection to GitLab, so CI jobs reach the cluster through that tunnel regardless of where the cluster is physically hosted — no inbound access to the laptop required, and it's the architecture that would carry forward cleanly into the eventual cloud-cluster phase of the roadmap.

Agent registration was attempted via the project UI (**Operate → Kubernetes clusters → Connect a cluster**) and failed instance-wide with:
```
Failed to register an agent
Gitlab::Kas::Client::ConfigurationError
```
This is a server-side misconfiguration of the KAS backend on `gitlab-edu.supsi.ch` itself, not something fixable at the project level. Flagged to the instance administrators; the Agent approach should be revisited if/when resolved, since it remains the better long-term fit.

### 4.4 Deploy Mechanism — Interim: Project-Specific Runner on the Laptop
Given the Agent is blocked at the infrastructure level, the deploy stage instead uses a **second, project-specific GitLab Runner registered directly on the laptop hosting Minikube** (runner name `pc-k8s`, `shell` executor). Since the runner *is* the machine running the cluster, `kubectl` simply uses the already-configured local Minikube context — no agent, no kubeconfig tunneling, no network problem to solve.

**Tradeoff, stated explicitly:** a shell-executor runner means CI script executes directly on that laptop with whatever permissions the runner's account holds. Reasonable and proportionate for a solo, thesis-scale project; not a pattern to carry into any setup with multiple contributors pushing to the pipeline.

The deploy job is `when: manual` rather than automatic: the laptop, Minikube, and runner are not guaranteed to be running when a pipeline fires, so the rollout is triggered deliberately once the cluster is confirmed up, rather than racing a possibly-offline runner.

### 4.5 kubeconfig Discovery Issue
The first deploy attempt failed with:
```
dial tcp [::1]:8080: connect: connection refused
The connection to the server localhost:8080 was refused
```
`localhost:8080` is `kubectl`'s hardcoded last-resort default when it finds **no usable kubeconfig/context at all** — not evidence of the wrong context, but of no context. Root cause: the shell executor's job environment is a non-interactive shell that doesn't source `~/.bashrc`/`~/.zshrc`, so `kubectl` didn't pick up the kubeconfig the same way it does in an interactive terminal session.

**Fix:** explicitly export `KUBECONFIG` as the first line of the job script:
```yaml
deploy:
  stage: deploy
  when: manual
  tags:
    - pc-k8s
  script:
    - export KUBECONFIG="$HOME/.kube/config"
    - kubectl config current-context
    - kubectl cluster-info
    - kubectl rollout restart deployment/frontend -n imageprocessing
    - kubectl rollout restart deployment/orchestrator -n imageprocessing
    - kubectl rollout restart deployment/worker -n imageprocessing
```
`kubectl config current-context` and `kubectl cluster-info` are kept in the script as a lightweight sanity check — if the runner or cluster context ever silently changes, this surfaces it immediately in the job log rather than failing opaquely on the first `rollout restart`.

## 5. Sprint Completion Status (Part 1)

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Pipeline Setup (#56) | Base `.gitlab-ci.yml` functional on runners | Done | Shell runner discarded for Docker executor (test/build stages); a second, project-specific shell runner (`pc-k8s`) added later specifically for deploy |
| Java Tests (#57) | `mvn test` executed, JaCoCo coverage parsed | Done | |
| Python Tests (#58) | `pytest` executed, XML junit artifacts saved | Done | Fast execution via `uv` cache and mocking; `pytest-cov` Cobertura report added for coverage parity with Java |
| Integration Tests (#79) | Testcontainers execution | **Blocked** | Fails due to lack of DIND socket. Set to `allow_failure: true` |
| Image Builds (#59) | Kaniko builds and pushes 3 OCI images | Done | `$CI_COMMIT_REF_SLUG` fixes slash issues in branch names; `--skip-tls-verify-registry` is a documented, deliberate tradeoff (§3.2) |
| CD Kubernetes (#80) | Cluster pulls registry images | Done | `gitlab-registry-secret` added; `imagePullPolicy: Always` enforced |
| Automated Deploy (#55) | Deploy job triggers rollout on the cluster | Done (interim) | GitLab Agent for Kubernetes blocked instance-wide by `Gitlab::Kas::Client::ConfigurationError` (§4.3) — flagged to instance admins. Interim: manual deploy job on a project-specific laptop runner (§4.4–4.5) |
