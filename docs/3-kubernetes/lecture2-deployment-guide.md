# Deployment Guide: Lecture 2 (ConfigMaps, Secrets, PV/PVCs, & Manual IP Routing)

This guide outlines the sequence required to deploy the `lecture2` environment. While configuration is now decoupled using ConfigMaps and Secrets, the lack of Kubernetes Services means we still must deploy pods sequentially and inject dynamic IPs into the ConfigMaps before starting downstream dependencies.

## Prerequisites
Ensure your Minikube cluster is completely clean. 
    ``` bash
    kubectl delete namespace imageprocessing --ignore-not-found
    kubectl delete pv pv-shared-storage --ignore-not-found
    ```
---

## Phase 1: Baseline Architecture
Establish the Namespace, Storage, and Secrets first. These do not depend on any IPs.

    kubectl apply -f lecture2/namespace.yml
    kubectl apply -f lecture2/storage/
    kubectl apply -f lecture2/postgres/secret.yml

*(Note: Database credentials are now safely stored in the Secret and automatically injected into the Pods.)*

---

## Phase 2: Database Setup
1. **Start Postgres:** 
    ```bash
    kubectl apply -f lecture2/postgres/pod.yml
    ```
2. **Retrieve the Postgres IP:**
    Wait for the pod to be `Running`.
    ``` bash
    kubectl get pod postgres -n imageprocessing -o wide
    ```
3. **Update and Apply ConfigMaps:**
    Edit `lecture2/postgres/configmap.yml` and `lecture2/orchestrator/configmap.yml`. Replace the `POSTGRES_HOST` value with the Postgres IP you just retrieved.
    ``` bash
    kubectl apply -f lecture2/postgres/configmap.yml -f lecture2/orchestrator/configmap.yml
    ```
---

## Phase 3: Message Broker Setup
1. **Prepare Kafka Configuration:**
   * Remove `KAFKA_ADVERTISED_LISTENERS` and `KAFKA_CONTROLLER_QUORUM_VOTERS` from `lecture2/kafka/configmap.yml`.
   * Add them to `lecture2/kafka/pod.yml` under the explicit `env` block using the `POD_IP` Downward API trick.
2. **Start Kafka and Retrieve IP:**
    ```bash
    kubectl apply -f lecture2/kafka/configmap.yml -f lecture2/kafka/pod.yml
    kubectl get pod kafka -n imageprocessing -o wide
    ```
3. **Update Downstream Configurations:**
   * Edit `lecture2/orchestrator/configmap.yml` and `lecture2/worker/configmap.yml`. Change `KAFKA_BOOTSTRAP_SERVERS` to `<KAFKA_IP>:9092`.
   * Edit `lecture2/kafka/job.yml`. Replace all instances of `kafka:9092` with `<KAFKA_IP>:9092`.
4. **Apply Updates:**
    ```bash
    kubectl apply -f lecture2/orchestrator/configmap.yml -f lecture2/worker/configmap.yml
    kubectl apply -f lecture2/kafka/job.yml
    ```
---

## Phase 4: Orchestrator & Worker Setup
1. **Start Orchestrator and Retrieve IP:**
    ```bash
    kubectl apply -f lecture2/orchestrator/pod.yml
    kubectl get pod orchestrator -n imageprocessing -o wide
    ```
2. **Update Frontend ConfigMap:**
    Edit `lecture2/frontend/configmap.yml`. Change `ORCHESTRATOR_URL` to `http://<ORCHESTRATOR_IP>:8080/internal`.
    ``` bash
    kubectl apply -f lecture2/frontend/configmap.yml
    ```
3. **Start the Worker:**
    ```bash
    kubectl apply -f lecture2/worker/pvc-model-cache.yml -f lecture2/worker/pvc-rembg-cache.yml
    kubectl apply -f lecture2/worker/pod.yml
    ```
---

## Phase 5: Frontend & Network Access
1. **Deploy Frontend:**
    ```bash
    kubectl apply -f lecture2/frontend/pod.yml
    ```
2. **Update Local Hosts File:**
    Map the hardcoded frontend domain to your local machine:
    ```bash
    sudo nvim /etc/hosts
    ```
    # Add the following line: `127.0.0.1   api.imageprocessing.local`

3. **Create a Privileged Port-Forward Tunnel:**
    Forward your local port `80` to the frontend pod's port `8080`.
    ```bash
    sudo -E kubectl port-forward pod/frontend -n imageprocessing 80:8080
    ```
4. **Access the Application:**
   Open your browser and navigate to:
   **http://api.imageprocessing.local**
