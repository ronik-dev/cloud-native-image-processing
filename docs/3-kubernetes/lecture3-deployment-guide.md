# Deployment Guide: Lecture 3 (Deployments, StatefulSets, Services & Ingress)

This guide outlines the streamlined deployment of the `lecture3` environment. Because this architecture uses Kubernetes Services for internal DNS resolution and Deployments/StatefulSets for self-healing, we do not need to manually pass IP addresses. 

## Prerequisites
1. **Clean the Cluster:** Ensure no orphaned resources remain from previous tests.
    ```bash
    kubectl delete namespace imageprocessing --ignore-not-found
    kubectl delete pv pv-shared-storage --ignore-not-found
    ```

2. **Enable Minikube Ingress:** Lecture 3 uses an NGINX Ingress controller for external access.
    ```bash
    minikube addons enable ingress
    ```
---

## Phase 1: Pre-Deployment Fixes
Before deploying, patch the configuration files to fix the missing namespaces and missing init container variables.

## Phase 1: Deploy the Architecture
Because internal DNS handles the routing, you can deploy the entire directory simultaneously. 

1. **Apply All Resources:**
    kubectl apply -R -f lecture3/

2. **Monitor the Rollout:**
    Watch the pods spin up. You will see some pods naturally restart or wait as their upstream dependencies (Postgres, Kafka) initialize.
    ```bash
    kubectl get pods -n imageprocessing -w
    ```
---

## Phase 2: External Network Access
Lecture 3 uses an Ingress controller configured to listen for `api.imageprocessing.local`.

1. **Update Local Hosts File:**
    Map the Ingress domain to your local machine:
    ```bash
    sudo nano /etc/hosts
    ```
    **Add the following line:** `127.0.0.1   api.imageprocessing.local`

2. **Start Minikube Tunnel:**
   Because Minikube runs in an isolated network environment, you must run the tunnel command to expose the Minikube Ingress controller to your host machine's localhost (port 80). 
   *Leave this running in a separate terminal window.*
    ```bash
    minikube tunnel
    ```

3. **Access the Application:**
   Open your browser and navigate to:
   **http://api.imageprocessing.local**
