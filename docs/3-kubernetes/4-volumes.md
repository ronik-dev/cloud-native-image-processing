# 4 K8s Volumes

> This guide assumes you have completed the labels and services setup described in `3-labels-and-services.md`.

---

### The problem: ephemeral container storage

By default, any data written inside a container is lost when the pod restarts. For stateless services like gateway this is fine, but for this architecture two categories of data must survive restarts:

- **ML model files** , the worker downloads the rembg (~200MB) and HuggingFace (~164MB) models at startup. Without persistence, every pod restart triggers a fresh download, which fails entirely in a network-restricted environment like Minikube.
- **Processed image files** , the shared `STORAGE_DATA_DIR` where orchestrator writes uploaded images and worker writes processed outputs. Without persistence, in-flight jobs lose their source files across restarts.

Kubernetes solves this with PersistentVolumes (PV) and PersistentVolumeClaims (PVC).

---

### PersistentVolume and PersistentVolumeClaim

A **PersistentVolume** is a piece of storage in the cluster , it describes where and how data is stored (hostPath, NFS, cloud disk, etc.). PVs are cluster-scoped, not namespaced.

A **PersistentVolumeClaim** is a request for storage by a pod. It describes how much storage is needed and what access mode is required. Kubernetes binds a PVC to a matching PV. PVCs are namespaced.

Pods reference PVCs, never PVs directly.

---

### Dynamic vs static provisioning

**Dynamic provisioning** , the cluster automatically creates a PV when a PVC is submitted, using a `StorageClass` as the provisioner. No PV manifest is needed. Minikube ships with a `standard` StorageClass that handles this automatically. Used for the ML model caches since they are single-pod mounts with no sharing requirements.

**Static provisioning** , a PV is created manually and a PVC binds to it explicitly by name via `volumeName`. Used for the shared storage volume because dynamic provisioning cannot produce a `ReadWriteMany` volume with Minikube's default StorageClass.

---

### ML model caches (dynamic, ReadWriteOnce)

Two PVCs are created for the worker's model caches using dynamic provisioning against the `standard` StorageClass:

- `pvc-model-cache` , mounted at `/home/appuser/.cache/huggingface`
- `pvc-rembg-cache` , mounted at `/home/appuser/.u2net`

Both use `ReadWriteOnce` since only the worker pod accesses them. Kubernetes automatically provisions the backing PVs and binds them. The model files downloaded on first startup persist across pod restarts, eliminating re-downloads.

---

### Shared storage (static, ReadWriteMany)

The `STORAGE_DATA_DIR` (`/data/imageprocessing`) is accessed by both orchestrator (writes uploaded images) and worker (reads source files, writes processed outputs). Two pods mounting the same volume simultaneously requires `ReadWriteMany`.

Minikube's default `standard` StorageClass only supports `ReadWriteOnce`, so static provisioning is used instead. A PV is defined manually with `hostPath` pointing to `/data/shared-storage` on the Minikube node, and a PVC binds to it by name using `storageClassName: manual`.

This is a known Minikube limitation. On a cloud cluster (GKE, AKS, EKS) the correct approach is a `ReadWriteMany`-capable StorageClass such as NFS or a cloud-native file storage backend. This is addressed when migrating to cloud in a later sprint.

---

### Reclaim policy

The `pv-shared-storage` PV uses `Retain` reclaim policy (the default for statically provisioned PVs) , if the PVC is deleted, the PV and its data are kept and must be manually reclaimed. The dynamically provisioned model cache PVs use `Delete` , when their PVC is deleted, the PV and data are automatically removed.

---

### Final state

```
kubectl get pvc -n imageprocessing

NAME                 STATUS   VOLUME              CAPACITY   ACCESS MODES   STORAGECLASS
pvc-model-cache      Bound    <dynamic>           10Gi       RWO            standard
pvc-rembg-cache      Bound    <dynamic>           10Gi       RWO            standard
pvc-shared-storage   Bound    pv-shared-storage   5Gi        RWX            manual
```

All four pods run successfully. The worker starts without attempting to re-download models on every restart, and the shared storage directory is accessible to both orchestrator and worker.
