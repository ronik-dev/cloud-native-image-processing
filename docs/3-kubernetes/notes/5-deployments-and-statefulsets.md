# 5 K8s Deployments and StatefulSets

> This guide assumes you have completed the volumes setup described in `4-volumes.md`.

---

### The problem: unmanaged pod lifecycles

Until now, the architecture has relied on bare Pods (`kind: Pod`). While this proved that the containers can run, communicate, and mount storage, bare pods are fundamentally fragile. If a node crashes, or if the Kubernetes out-of-memory (OOM) killer terminates a pod, a bare pod is gone forever. Furthermore, manual scaling is impossible without duplicating manifests.

To make the system resilient, the bare pod manifests must be migrated to higher-level workload controllers that automatically manage the pod lifecycle and ensure high availability.

---

### Deployments (stateless tier)

The bare `pod.yml` manifests for the stateless services are converted to `deployment.yml` manifests:

- **gateway** , manages API routing.
- **orchestrator** , coordinates jobs.
- **worker** , executes ML tasks.

Deployments wrap the original pod specifications inside a `spec.template.spec` block. The environment variables, ConfigMap references (`envFrom`), and volume mounts remain exactly the same as they were in the bare pods. Because these services are stateless (the worker and orchestrator write to shared PVCs, not local container storage), Deployments can freely destroy and recreate them across nodes without data loss.

If any of these containers crash, the Deployment controller instantly spins up a replacement to maintain the desired `replicas: 1` state.

---

### StatefulSets (data tier)

Postgres cannot be managed by a standard Deployment. Databases require strict guarantees about startup ordering, stable network identities, and sticky storage attachments.

The postgres bare pod is converted to a **StatefulSet**. This provides:
- **Stable DNS** , the pod is always predictably named `postgres-0`, rather than getting a random hash suffix like a Deployment pod.
- **Volume Claim Templates** , instead of manually creating a PVC and binding it to the pod, the StatefulSet uses a `volumeClaimTemplates` block to dynamically provision storage for each replica. When `postgres-0` restarts, Kubernetes guarantees it is reattached to the exact same persistent volume, ensuring no database records are lost.

---

### Final state

After deleting the old bare pods and applying the new manifests, the cluster transitions to managed workloads:

```bash
kubectl get deployments,statefulsets -n imageprocessing

NAME                           READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/gateway        1/1     1            1           ...
deployment.apps/orchestrator   1/1     1            1           ...
deployment.apps/worker         0/1     1            1           ...

NAME                        READY   AGE
statefulset.apps/postgres   1/1     ...
``` 

> note: the worker will still fail as it have no internet access yet

All workloads are now resilient. The Deployment controllers will automatically recreate the worker, gateway, or orchestrator if they crash, and the StatefulSet protects the postgres database's identity and persistent data.
