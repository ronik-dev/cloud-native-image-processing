# 7 K8s Probes

> This guide assumes Deployments and the StatefulSet are in place as described in the previous section.

---

### Why probes matter

Without probes, Kubernetes has no way to distinguish a pod that is genuinely ready to serve traffic from one that is still initializing or silently broken. It routes traffic to all Running pods regardless of their internal state, and only restarts a pod if the process itself crashes. Probes give Kubernetes the signal it needs to make smarter decisions.

---

### Probe types

**Readiness probe** — tells Kubernetes when a pod is ready to receive traffic from a Service. Until the readiness probe passes, the pod is removed from the Service's endpoint list. This is the primary fix for the startup ordering problem: without it, the Service routes requests to orchestrator before it has connected to postgres, producing connection errors rather than retries.

**Liveness probe** — tells Kubernetes when a pod is broken and needs to be restarted. It runs continuously throughout the pod's lifetime. A pod that passes its readiness probe but later becomes deadlocked or unresponsive will be caught by the liveness probe and restarted.

**Startup probe** (not implemented, documented for reference) — a third probe type designed specifically for slow-starting containers. It disables both liveness and readiness until it succeeds, which is cleaner than setting very high `initialDelaySeconds` on the liveness probe. The worker is a good candidate for this in a future iteration — its ML model loading takes 60-120 seconds, and a startup probe would handle that window more precisely than a blanket delay.

---

### Probe mechanisms

Two mechanisms are used in this architecture:

**`httpGet`** — Kubernetes sends an HTTP GET to a specified path and port. A 2xx or 3xx response is success. Used for Spring Boot services via the `/actuator/health` endpoint (gateway, orchestrator) and for the FastAPI worker via `/health`.

**`exec`** — Kubernetes runs a command inside the container. Exit code 0 is success. Used for postgres via `pg_isready`, which is the native postgres health check utility.

---

### Key timing decisions

**`initialDelaySeconds`** — how long Kubernetes waits after container start before firing the first probe. Setting this too low causes false failures during initialization; too high delays traffic routing unnecessarily.

**`livenessProbe.initialDelaySeconds` must be higher than `readinessProbe.initialDelaySeconds`** — if liveness fires before the service is ready, it kills pods that are still legitimately starting up. For orchestrator, readiness starts at 15s and liveness at 60s. For the worker, readiness starts at 120s and liveness at 150s to account for ML model loading time.

**Postgres liveness at 60s** — postgres 18 takes longer to initialize than earlier versions, especially on first boot when it creates the database and user. Setting liveness too low caused Kubernetes to kill the pod mid-initialization, which is why the delay was increased from the default.

---

### Postgres volume mount path

The postgres StatefulSet mounts the PVC at `/var/lib/postgresql` rather than `/var/lib/postgresql/data`. Postgres 18 Alpine enforces strict ownership and permission checks on the data subdirectory — mounting directly to `/data` caused the engine to intentionally crash on startup with a permissions error. Mounting one level up allows postgres to manage the `data/` subdirectory itself.

---

### Shared volume permissions

The shared `pvc-shared-storage` PVC is mounted at `/data/imageprocessing` by both orchestrator and worker. The orchestrator runs as a non-root `appuser` and requires write access to this directory. The hostPath directory on the Minikube node was created with root ownership by default, causing `Permission Denied` errors when orchestrator attempted to write uploaded images.

Fixed by granting write permissions directly on the Minikube node:

```bash
minikube ssh -- sudo chmod 777 /data
```

This is a local development workaround. On a cloud cluster the correct approach is a `securityContext` in the pod spec with `fsGroup` set to the application's GID, which instructs Kubernetes to recursively chown the mounted volume to that group on pod startup.

---

### Service DNS alignment

During this phase the gateway ConfigMap was updated to use the full Kubernetes Service name for the orchestrator:

```
ORCHESTRATOR_URL: http://orchestrator-service:8080/internal
```

The previous value (`http://orchestrator:8081/internal`) referenced the pod name rather than the Service name, which caused `NXDOMAIN` resolution failures once Deployments replaced bare Pods. Kubernetes internal DNS resolves Service names, not pod names.
