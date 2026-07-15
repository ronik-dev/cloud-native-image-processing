# 3 K8s Labels and Services

> This guide assumes you have completed the namespace and ConfigMap setup described in `2-namespaces-configmaps-secrets.md`.

---

### The problem: inter-pod DNS

In Docker Compose, container names are automatically registered as DNS entries. Any service can reach `postgres` simply because the container is named `postgres`. Kubernetes does not work this way — bare pods have no DNS entry. Inter-pod communication requires a `Service` object, which registers a stable DNS name in the cluster's internal DNS and proxies traffic to matching pods via a label selector.

Without a Service in front of postgres, the orchestrator throws `UnknownHostException: postgres` at startup regardless of how `POSTGRES_HOST` is configured.

---

### Labels

Labels are key-value pairs attached to Kubernetes objects. They are the mechanism Services use to find their target pods — a Service's `selector` field matches pods by label. Every pod that needs to be reachable by a Service must carry a matching label.

All pods in this architecture are labeled with `app: <service-name>` and `layer: <tier>`:

| Pod | Labels |
|---|---|
| gateway | `app: gateway`, `layer: frontend` |
| orchestrator | `app: orchestrator`, `layer: backend` |
| worker | `app: worker`, `layer: backend` |
| postgres | `app: postgres`, `layer: data` |

The label key used in the Service `selector` must match exactly — `app: postgres` in the selector will not match a pod labeled `name: postgres`.

---

### Services

A Service provides a stable DNS name and virtual IP that proxies traffic to pods matching its selector. Four Services are defined, one per pod.

**ClusterIP** (default) — exposes the service only inside the cluster. Used for postgres, orchestrator, and worker since they are internal services that should never be reached directly from outside.

**NodePort** — exposes the service on a port of the Minikube node, making it reachable from the host machine. Used only for gateway since it is the single external entry point.

The Service name becomes the DNS hostname. Because the orchestrator configmap sets `POSTGRES_HOST: postgres` and `WORKER_URL: http://worker:8082`, the Services must be named `postgres` and `worker` respectively — not `postgres-service` or `worker-service`. Misnaming the Service causes `UnknownHostException` at runtime even though the pods themselves are running.

---

### Secret key mismatch: postgres vs Spring Boot

The official `postgres:18-alpine` image initializes the database on first startup using three specific environment variable names: `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB`. These are hardcoded in the image entrypoint.

Spring Boot's `application.properties` references different names: `POSTGRES_USER_NAME` and `POSTGRES_USER_PASSWORD`. Using `envFrom` with a single secret that has one naming convention satisfies one side but breaks the other.

The solution is to keep the secret keys matching what postgres expects (`POSTGRES_USER`, `POSTGRES_PASSWORD`), and map them explicitly in the orchestrator pod spec using `env.valueFrom.secretKeyRef` rather than `envFrom`. This lets each consumer reference the same secret keys under the name it expects, without duplicating the secret.

---

### Stale postgres data

The postgres image only runs its initialization script (creating the user and database) if the data directory is empty. If the pod was previously started with different credentials, the data directory retains the old user and the new credentials are silently ignored — causing authentication failures even after the secret is corrected.

Since volumes are not yet configured (ticket #64), the postgres data directory lives on the Minikube node's disk and survives pod deletion. To force a clean reinitialization, the data directory must be wiped manually:

```bash
minikube ssh -- sudo rm -rf /var/lib/postgresql
```

This is a known limitation of running stateful services without PersistentVolumes. The volumes ticket will replace this with a proper PVC, making the initialization reproducible and the data genuinely persistent across pod restarts.

---

### Current state

All four pods start successfully in the `imageprocessing` namespace. Gateway, postgres, and orchestrator run cleanly. The orchestrator connects to postgres via the `postgres` Service and initializes the schema on startup.

The worker pod crashes because it attempts to download the rembg ML model from GitHub at startup, and Minikube has no internet access in this environment. This is resolved in ticket #64 — in Docker Compose the model cache was persisted via a named volume (`rembg_cache`) so the model survived restarts without re-downloading. The equivalent PersistentVolume will be added in the next sprint.

The OTLP metrics warning in the orchestrator logs (`Failed to publish metrics to OTLP receiver`) is expected — the OpenTelemetry collector is not deployed yet. It will be addressed in the service mesh milestone.
