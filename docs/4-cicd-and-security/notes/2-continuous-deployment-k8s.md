# 2 Continuous Deployment to Kubernetes (Task 80)
> This guide outlines the transition to Continuous Deployment (CD), configuring the Kubernetes cluster to securely authenticate with the GitLab registry and pull new images automatically.

### Context
With the pipeline successfully building and pushing images (Task 59), the Kubernetes cluster must be configured to fetch them. Previously, images were built directly on the Minikube node and the deployments used `imagePullPolicy: Never`. 

To enable CD, the cluster must authenticate with the private `gitlab-edu.supsi.ch:5050` registry, and the deployments must force a re-pull on every restart.

### 1. GitLab Deploy Token & Kubernetes Secret
Personal credentials should never be used for automated infrastructure. A dedicated **GitLab Deploy Token** (named `gitlab+deploy-token-30`) with the `read_registry` scope was generated in the GitLab repository settings.

This token was injected into the Kubernetes `imageprocessing` namespace as a `docker-registry` secret using an imperative dry-run to generate the necessary base64-encoded `.dockerconfigjson`:

kubectl create secret docker-registry gitlab-registry-secret \
  --docker-server=gitlab-edu.supsi.ch:5050 \
  --docker-username=<TOKEN-NAME> \
  --docker-password=<TOKEN> \
  --namespace=imageprocessing \
  --dry-run=client -o yaml > registry-secret.example.yml

*(Note: the actual file with the real token was applied to the cluster and explicitly added to `.gitignore` to prevent credential leaking).*

### 2. Updating the Deployment Manifests
The `frontend`, `orchestrator`, and `worker` Deployments were patched to integrate the new remote registry architecture:

1. **Registry URL:** Image strings were updated from local tags (`imageprocessing/worker:latest`) to the fully qualified GitLab registry URLs.
2. **Pull Policy:** Changed from `imagePullPolicy: Never` to `imagePullPolicy: Always`. This forces the kubelet to check the registry for a new digest even if the floating branch tag (like `:dev`) hasn't changed.
3. **Authentication:** Added `imagePullSecrets: [{ name: gitlab-registry-secret }]` to the Pod specification, granting the deployments permission to use the deploy token.
