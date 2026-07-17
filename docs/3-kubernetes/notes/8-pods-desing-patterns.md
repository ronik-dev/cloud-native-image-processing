# 8 Pod Design Patterns & Initialization

> This guide details the implementation of pod startup sequencing and the evaluation of multi-container design patterns for the microservice architecture.

---

### The Startup Ordering Problem

Unlike Docker Compose, which allows strict sequential startup using `depends_on: condition: service_healthy`, Kubernetes starts all pods in a cluster concurrently. 

Because the orchestrator pod spins up at the exact same time as the postgres pod, it previously attempted to establish database connection pools before the database engine had finished initializing. While increasing probe delays (documented in Phase 7) prevented the pod from being prematurely killed by Kubernetes, it still resulted in ugly application-level crash loops and errors during the initial boot phase.

---

### The Init Container Pattern

To gracefully manage dependencies, the **Init Container** pattern was implemented in the orchestrator deployment. 

Init containers are specialized containers that run sequentially before the main application containers in a pod. If an init container fails, Kubernetes restarts it until it succeeds, blocking the main container from starting.

A `wait-for-postgres` init container was added to the orchestrator pod:
* **Image:** `postgres:18-alpine` (providing native database client tools).
* **Execution:** It runs a lightweight shell script executing `pg_isready` in a 2-second loop. 
* **Result:** The main `imageprocessing/orchestrator` container is now held in a `PodInitializing` state and is completely prevented from starting until the Postgres database is fully online and accepting connections.

---

### Re-tuning the Probes

Because the Init Container absorbs the chaotic waiting period during concurrent startup, the main orchestrator container is now guaranteed a healthy database upon booting. 

As a result, the `initialDelaySeconds` on the orchestrator's `livenessProbe`—which was temporarily extended to 60 seconds as a workaround—was safely reduced back down to **15 seconds**. This closes the blind spot in K8s crash detection, ensuring that if the orchestrator crashes for unrelated reasons, Kubernetes will notice and restart it almost immediately.

---

### Evaluation of Alternative Pod Patterns

During this architectural phase, other standard multi-container K8s design patterns were evaluated but ultimately dismissed as unnecessary or out of scope for this thesis:

* **Sidecar Pattern:** Typically used to deploy helper processes alongside a main application (e.g., a localized logging agent or a reverse proxy). Because the application logs are natively handled by Kubernetes standard output, and internal networking is managed by K8s Services and the Gateway, a sidecar would only add unnecessary overhead.
* **Adapter Pattern:** Used to normalize output from a main container to match centralized monitoring standards (e.g., adapting custom application metrics for Prometheus). Since the Spring Boot applications natively output standardized metrics via Micrometer and standard JSON structures, an adapter container was not required.
