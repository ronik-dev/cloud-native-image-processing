# 2 K8s Namespace, ConfigMaps and Secrets
> This guide assumes you have completed the K8s setup described in `1-k8s-setup.md` and have a running Minikube cluster with the four base pods applied.

---

### Namespace

A dedicated namespace `imageprocessing` isolates all application resources from the `default` namespace and from any other workloads running in the cluster. All pod specs declare `namespace: imageprocessing` in their metadata.

> Once a namespace is added to pod specs, kubectl commands require the `-n imageprocessing` flag to target them. `kubectl get pods` without the flag will show resources in `default` only.

---

### Secrets

Sensitive credentials are stored in a Kubernetes `Secret` rather than a `ConfigMap` or hardcoded in pod specs. The only secret in this architecture is the PostgreSQL credentials, shared between the `postgres` and `orchestrator` pods since both need to authenticate against the same database.

**Never commit `secret.yml` to the repository.** Add it to `.gitignore`:

```
k8s/**/secret.yml
```

A `secret.example.yml` is committed instead as a reference template, following the same pattern as `application-local.properties` from Sprint 1. Anyone setting up the project copies the example, fills in real values, and the actual secret stays off the repository.

---

### ConfigMaps

Non-sensitive configuration is stored in a `ConfigMap` per service. Each service only declares the environment variables it needs — no service can read another service's ConfigMap. This mirrors the principle of least privilege and makes it safe to change one service's config without risking unintended side effects on another.

The environment variables map directly from the `compose.yml` defined in Sprint 2, with one important correction: URL values must include the `http://` scheme. Docker Compose tolerates bare hostnames in some cases; Kubernetes does not.

---

### Injecting configuration into pods

All pod specs are updated to use `envFrom` rather than listing individual `env` entries. `envFrom` pulls every key from a Secret or ConfigMap into the container as environment variables in a single block, keeping the pod spec clean and decoupled from the specific variable names.

Pods that need database access reference both the `postgres-secret` and their own ConfigMap. Pods with no sensitive config reference only their ConfigMap.

---

### Current state

All four pods start in the `imageprocessing` namespace with correct configuration injected. `postgres`, `gateway`, and `worker` run successfully. `orchestrator` crashes because it cannot resolve the hostname `postgres` — in Kubernetes, inter-pod DNS resolution requires a `Service` object in front of each pod. Unlike Docker Compose where container names are automatically registered as DNS entries, Kubernetes only registers Services in its internal DNS. This is addressed in the next ticket.
