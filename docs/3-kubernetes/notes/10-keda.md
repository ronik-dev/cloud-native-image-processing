### Test
``` Bash
kubectl exec -it kafka-0 -n imageprocessing -- /bin/bash -c "
for i in {1..30}; do
  echo '{\"job_id\": \"scale-test-'$i'\", \"job_type\": \"BACKGROUND_REMOVAL\", \"source_sk\": \"8cef3e8b-7c2e-49d4-a233-2b49c9d7c633\", \"target_sk\": \"scale-out-'$i'.png\"}'
done | /opt/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic job.requests
"
````

> Continues `9-kafka.md`, which covers the Kafka request/result queue itself. This document covers the remaining Sprint 3 item: elastic Worker scaling driven by that queue, and the storage decisions that scaling forced to the surface.


## 1. Architecture Overview

With the Orchestrator -> Worker call now mediated by Kafka (`sprint-3-kafka-recap.md`, §1), Worker replica count can scale independently of the Orchestrator, driven directly by how far behind the `job.requests` consumer group has fallen — rather than by CPU/memory, which is a poor proxy for "how many images are queued."

**Key objects, current state (`k8s/`):**

| Resource | File | Role |
|---|---|---|
| `ScaledObject` | `keda.yml` | Watches `job.requests` lag for consumer group `ai-worker`, scales `Deployment/worker` |
| `Job` | `job.yml` | One-shot topic creation (`sprint-3-kafka-recap.md`, §3.6) — now also the source of the scaling ceiling, see §3.1 |
| `PersistentVolume`/`PersistentVolumeClaim` | `pv-shared-storage.yml`, `pvc-shared-storage.yml` | Shared upload/output storage, revisited in §3.3 |
| `PersistentVolumeClaim` | `pvc-model-cache.yml`, `pvc-rembg-cache.yml` | ML model caches, open item — see §3.4 |

---

## 2. Motivation: KEDA over Plain HPA

The standard `HorizontalPodAutoscaler` scales on CPU/memory utilization by default. Neither metric reflects the actual bottleneck here: a Worker pod doing PyTorch inference can sit at high CPU with only one job in flight, while ten queued jobs waiting behind it produce no CPU signal at all until they're picked up. **Consumer lag on `job.requests`** — the count of unconsumed messages per partition — is the metric that actually describes backlog.

KEDA's `kafka` scaler reads that lag directly from the broker and drives a standard Kubernetes HPA under the hood (KEDA installs itself as a metrics adapter, not a replacement for the HPA controller). This means:

- Scaling decisions track the real queue depth, not a CPU proxy for it.
- `minReplicaCount: 1` keeps one Worker warm at all times — relevant because model loading (`sprint-2-microservices-recap.md`, §6.3) takes long enough that scaling from zero would leave the first job in a cold-start queue for the full model-load window.
- Scale-up granularity is explicit and tunable (`lagThreshold: '10'`) rather than inferred from a resource curve.

---

## 3. Design Decisions

### 3.1 Topic Partitioning as the Scaling Ceiling

Kafka can only assign one partition per consumer within a consumer group — a partition is never split across two consumers. This means `maxReplicaCount` on the `ScaledObject` is not just a safety cap, it's bounded by the number of partitions on `job.requests`: any replica beyond the partition count receives no partitions at all and sits fully idle.

`keda.yml` sets `maxReplicaCount: 10`. `job.yml` was revised to create `job.requests` with **10 partitions** (`job.results` stays at 3 — it has no consumer that needs to scale, since only the Orchestrator's `JobResultListener` reads it). The two numbers are now aligned deliberately, not coincidentally:

```yaml
# job.yml
--create --if-not-exists --topic job.requests --partitions 10 --replication-factor 1
```

```yaml
# keda.yml
spec:
  minReplicaCount: 1
  maxReplicaCount: 10
```

If `maxReplicaCount` is ever raised again, `job.requests`'s partition count has to move with it — partitions can be *added* to an existing topic without data loss, but never reduced, so over-provisioning partitions up front is the safer direction to round in.

### 3.2 Namespace Consistency

All manifests — `namespace.yml`, `keda.yml`, `job.yml`, the storage PV/PVCs, `ingress.yml` — consistently use `imageprocessing` (one word, no hyphen). This matters more than it looks: `keda.yml`'s `bootstrapServers: kafka.imageprocessing.svc.cluster.local:9092` is a fully-qualified DNS name that silently resolves to nothing if the namespace segment doesn't match exactly.

**Correction to a prior deliverable:** the standalone `k8s/kafka/` bundle provided earlier in this project's chat history used `image-processing` (hyphenated) for the Kafka `ConfigMap`/`StatefulSet`/`Service`. That was inconsistent with every other manifest in this repo and, if applied as-is, would have produced an unresolvable `kafka` Service reference. The manifests actually on disk (verified in this recap) are consistently `imageprocessing` — if the hyphenated versions were ever applied to a cluster, they should be reconciled to match.

### 3.3 Shared Storage: Honest `ReadWriteOnce` over a `hostPath` That Never Enforced Access Modes

`pv-shared-storage.yml` was previously declared `ReadWriteMany` while backed by `hostPath` — a combination Kubernetes accepts syntactically but never actually enforces, since `hostPath` has no attach/detach step for the kubelet to gate on. In practice this meant:

- Two Worker pods scheduled to the **same** node transparently shared `/data/imageprocessing` — looked correct.
- A Worker pod scheduled to a **different** node silently got an empty, disconnected local directory instead — `FileNotFoundError` on any `source_sk` written by a pod on another node, with no scheduling error to explain why.

This is a **silent, scheduling-dependent failure mode**, not a hard error — the dangerous kind, since it's invisible on a single-node dev cluster and only appears once the Worker actually scales across nodes, which is precisely what §3.1 is designed to enable.

**Resolution for this sprint:** `pv-shared-storage.yml` / `pvc-shared-storage.yml` are now declared `ReadWriteOnce`, matching what `hostPath` can actually deliver. The underlying multi-node risk isn't eliminated by this relabeling — `hostPath` still doesn't check access modes — but the manifest no longer *claims* a guarantee it can't provide. The actual constraint is enforced operationally instead: **the cluster stays single-node (Minikube) until the storage backend changes.** On a single-node cluster every Worker replica lands on the same node by construction, so the multi-node inconsistency described above cannot occur.

This is documented here as a **deliberate, temporary constraint**, not an oversight:

| | Now (Minikube, single node) | Planned (NFS) |
|---|---|---|
| `storageClassName` | `manual` | NFS-backed (e.g. `nfs-client` via `nfs-subdir-external-provisioner`) |
| Backing mechanism | `hostPath` | Real network filesystem, multi-node safe |
| `accessModes` | `ReadWriteOnce` (honest) | `ReadWriteMany` (actually enforced) |
| Multi-node Worker scaling | Constrained to 1 node | Fully supported |

No `nodeAffinity`/`nodeSelector` was added to pin Workers to a specific node, because it would be redundant on a genuinely single-node cluster — there is only one node to schedule onto. **This is flagged as a follow-up:** if the cluster gains additional nodes before the NFS migration lands, that single-node assumption becomes implicit and unenforced again, and the original failure mode returns. An explicit `nodeAffinity` rule is the correct stopgap at that point, not before.

### 3.4 Deferred Decision: ML Model Cache PVCs

`pvc-model-cache.yml` and `pvc-rembg-cache.yml` remain `ReadWriteOnce` on the `standard` StorageClass, unchanged from Sprint 2 (`sprint-2-...`, and `4-volumes.md`). This is a **known open item**, not yet resolved this sprint:

- Unlike the shared storage volume, these two are candidates for a structurally different fix: since the `isnet-general-use` and `deformable-detr-with-box-refine` weights are static, read-mostly artifacts, they can be **baked into the Worker image at build time** instead of cached via a shared volume. That removes the RWX requirement entirely for these two volumes — no multi-node sharing is needed if every replica already has its own copy from the image layer.
- This wasn't addressed alongside §3.3 because it's an image-build change (Dockerfile + CI pipeline), not a manifest change, and was judged out of scope for this specific ticket. It should be picked up before `maxReplicaCount: 10` is actually exercised in practice — right now, a second Worker replica scheduled on a real multi-node cluster would hit the same silent-empty-mount problem on these two volumes that §3.3 just fixed for shared storage.

---

## 4. KEDA Configuration Reference

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: kafka-scaledobject
  namespace: imageprocessing
spec:
  scaleTargetRef:
    name: worker                # defaults to apps/v1 Deployment — matches deployment.yml
  minReplicaCount: 1             # keep one Worker warm; avoids scale-from-zero cold start (§2)
  maxReplicaCount: 10            # bounded by job.requests partition count (§3.1)
  triggers:
  - type: kafka
    metadata:
      bootstrapServers: kafka.imageprocessing.svc.cluster.local:9092
      consumerGroup: ai-worker           # must match KAFKA_CONSUMER_GROUP in worker-config
      topic: job.requests
      lagThreshold: '10'                 # +1 replica per 10 messages of backlog
      offsetResetPolicy: latest          # only affects a brand-new consumer group with no committed offset
```

`consumerGroup: ai-worker` and `topic: job.requests` are the same values the Worker's `aiokafka` consumer already uses (`sprint-3-kafka-recap.md`, §6.3) — `worker-config`'s `KAFKA_CONSUMER_GROUP=ai-worker` has to stay in lockstep with this field, since KEDA is reading lag for that specific named group, not the topic in general.

`offsetResetPolicy: latest` only takes effect if the group has no committed offset at all — for the long-running `ai-worker` group this is essentially moot day-to-day, but worth remembering if the group is ever renamed or reset: a fresh group would ignore any pre-existing backlog rather than scaling to drain it.

---

## 5. Kubernetes Deployment

Requires the KEDA operator installed cluster-wide once (not part of this application's namespace):

```bash
helm repo add kedacore https://kedacore.github.io/charts
helm repo update
helm install keda kedacore/keda --namespace keda --create-namespace
```

Application of this sprint's objects, in dependency order:

```bash
# Kafka + topics must exist before KEDA can read lag from them
kubectl apply -f k8s/kafka-config.yml
kubectl apply -f k8s/service.yml        # kafka headless Service
kubectl apply -f k8s/statefulset.yml    # kafka broker
kubectl apply -f k8s/job.yml            # kafka-create-topics (10 / 3 partitions)

# Storage — apply before the worker Deployment so PVCs are Bound in time
kubectl apply -f k8s/pv-shared-storage.yml
kubectl apply -f k8s/pvc-shared-storage.yml
kubectl apply -f k8s/pvc-model-cache.yml
kubectl apply -f k8s/pvc-rembg-cache.yml

# Worker + autoscaler
kubectl apply -f k8s/deployment.yml     # worker Deployment
kubectl apply -f k8s/keda.yml
```

---

## 6. Validation

`10-keda.md` documents the load test used to confirm scale-up behavior — publishing a burst of synthetic `BACKGROUND_REMOVAL` messages directly to `job.requests` via the Kafka console producer, bypassing the Orchestrator entirely to isolate the scaling behavior from the rest of the pipeline:

```bash
kubectl exec -it kafka-0 -n imageprocessing -- /bin/bash -c "
for i in {1..30}; do
  echo '{\"job_id\": \"scale-test-'$i'\", \"job_type\": \"BACKGROUND_REMOVAL\", \"source_sk\": \"8cef3e8b-7c2e-49d4-a233-2b49c9d7c633\", \"target_sk\": \"scale-out-'$i'.png\"}'
done | /opt/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic job.requests
"
```

30 messages against a `lagThreshold` of 10 should drive the Worker `Deployment` toward 3 replicas (`ceil(30 / 10)`), capped by `maxReplicaCount: 10` and, independently, by however many of the 10 partitions actually end up with backlog on them given the messages' (implicit, unkeyed) partition assignment.

**Follow-up validation not yet performed:** confirming that all 10 replicas can actually be scheduled and reach `Ready` on the current single-node Minikube cluster — resource requests/limits aren't set on `deployment.yml`'s Worker container, so 10 concurrent PyTorch-loaded replicas may be constrained by the node's actual CPU/memory long before the KEDA ceiling is reached. Worth a resource-request pass before treating `maxReplicaCount: 10` as a validated ceiling rather than a configured one.

---

## 7. Known Limitations / Follow-up Items

| Item | Status | Notes |
|---|---|---|
| `job.requests` partition count vs `maxReplicaCount` | Resolved | Both set to 10 (§3.1) |
| Shared storage access mode honesty | Resolved | `RWO` on `hostPath`, matches reality (§3.3) |
| Shared storage multi-node scaling | **Open, deliberately deferred** | Blocked on NFS migration; single-node Minikube is the interim enforcement mechanism |
| ML model cache PVCs (`pvc-model-cache`, `pvc-rembg-cache`) | **Open** | Candidate fix: bake models into the Worker image at build time, removing the RWX requirement entirely (§3.4) |
| `nodeAffinity` on Worker deployment | **Not implemented, intentionally** | Redundant on a true single-node cluster; becomes necessary the moment a second node is added before NFS lands |
| Worker resource requests/limits | **Open** | Needed to know whether 10 replicas can actually be scheduled, independent of the KEDA/Kafka ceiling |
| Standalone `k8s/kafka/` bundle namespace typo | **Corrected here** | Prior deliverable used `image-processing`; actual manifests use `imageprocessing` throughout |
