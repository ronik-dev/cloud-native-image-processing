# 1 K8s Setup
> This guide is OS specific for Arch Linux, as this project is developed on this os
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes you have Docker installed and running, and pacman available.

### Tools and libraries

##### kubectl
kubectl [arch wiki](https://wiki.archlinux.org/title/Kubernetes)
This project uses kubectl v1.35.1
1. install:
    ```bash
    sudo pacman -S kubectl
    ```
2. verify the installation:
    ```bash
    kubectl version --client
    ```
    output should look like:
    ```bash
    Client Version: v1.35.1
    Kustomize Version: v5.6.0
    ```

##### Minikube
Minikube [official doc](https://minikube.sigs.k8s.io/docs/)
This project uses Minikube v1.38.1
1. install:
    ```bash
    sudo pacman -S minikube
    ```
2. verify the installation:
    ```bash
    minikube version
    ```
    output should look like:
    ```bash
    minikube version: v1.38.1
    commit: 57b8b4e2c5d37ee6fd3957a1de57c2e53ce3ab66
    ```
3. start the cluster:
    ```bash
    minikube start
    ```
    output should look like:
    ```bash
    😄  minikube v1.38.1 on Arch
    ✨  Using the docker driver based on existing profile
    👍  Starting "minikube" primary control-plane node in "minikube" cluster
    🐳  Preparing Kubernetes v1.35.1 on Docker 29.2.1 ...
    🔎  Verifying Kubernetes components...
    🌟  Enabled addons: storage-provisioner, default-storageclass
    🏄  Done! kubectl is now configured to use "minikube" cluster and "default" namespace by default
    ```
4. verify the cluster is running:
    ```bash
    kubectl get nodes
    ```
    output should look like:
    ```bash
    NAME       STATUS   ROLES           AGE   VERSION
    minikube   Ready    control-plane   10m   v1.35.1
    ```

---

### Building images for Minikube

Minikube runs its own Docker daemon, isolated from the host machine. Images built on the host are not visible to Minikube by default. To make local images available without pushing to a registry, point the shell at Minikube's Docker daemon before building:

```bash
eval $(minikube docker-env)
```

> **Note:** this command modifies environment variables only in the current shell session. Every new terminal needs to run it again, or the build will target the host daemon instead.

Now build the application images:

```bash
docker compose build
```

For third-party images like `postgres:18-alpine` that are already present on the host but should not be re-pulled, load them directly into Minikube:

```bash
minikube image load postgres:18-alpine
```

---

### Pod manifests

The `k8s/` directory at the repo root contains one subdirectory per service, each with a `pod.yml`:

```
k8s/
├── gateway/
│   └── pod.yml
├── orchestrator/
│   └── pod.yml
├── postgres/
│   └── pod.yml
└── worker/
    └── pod.yml
```

> **Note:** bare Pods are used here for simplicity. They are not restarted automatically if they crash — Deployments (introduced in a later ticket) handle that. For this first setup the goal is to confirm that Kubernetes can schedule and start each container.

Because the images are local and not pushed to any registry, every pod spec must include `imagePullPolicy: Never` to prevent Kubernetes from attempting to pull from Docker Hub.

##### gateway/pod.yml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: gateway
spec:
  containers:
    - name: gateway
      image: imageprocessing/gateway:latest
      imagePullPolicy: Never
      ports:
        - containerPort: 8080
```

##### orchestrator/pod.yml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: orchestrator
spec:
  containers:
    - name: orchestrator
      image: imageprocessing/orchestrator:latest
      imagePullPolicy: Never
      ports:
        - containerPort: 8081
```

##### worker/pod.yml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: worker
spec:
  containers:
    - name: worker
      image: imageprocessing/worker:latest
      imagePullPolicy: Never
      ports:
        - containerPort: 8082
```

##### postgres/pod.yml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: postgres
spec:
  containers:
    - name: postgres
      image: postgres:18-alpine
      imagePullPolicy: Never
      ports:
        - containerPort: 5432
```

> **Note:** no environment variables are provided here (no `POSTGRES_USER`, `POSTGRES_PASSWORD`, etc.). Postgres will crash without them — Secrets and ConfigMaps are introduced in a later ticket. The goal at this stage is structural: verify the manifests are valid and the pods are schedulable.

---

### Applying the manifests

Apply all manifests recursively from the repo root:

```bash
kubectl apply -R -f k8s/
```

output should look like:
```bash
pod/gateway created
pod/orchestrator created
pod/postgres created
pod/worker created
```

Check the pod statuses:

```bash
kubectl get pods
```

output should look like:
```bash
NAME           READY   STATUS             RESTARTS   AGE
gateway        1/1     Running            0          30s
orchestrator   0/1     CrashLoopBackOff   1          30s
postgres       0/1     CrashLoopBackOff   1          30s
worker         1/1     Running            0          30s
```

`gateway` and `worker` start successfully because they have no hard startup dependencies. `orchestrator` and `postgres` crash — expected at this stage due to missing environment variables and no inter-service connectivity. Both are resolved in subsequent tickets.

To inspect why a pod is crashing:

```bash
kubectl logs <pod-name>
```

for example:
```bash
kubectl logs postgres
```
```bash
Error: Database is uninitialized and superuser password is not specified.
       You must specify POSTGRES_PASSWORD to a non-empty value for the superuser.
```
