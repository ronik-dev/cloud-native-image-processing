# Deployment Guide: Lecture 1 (Raw Pods & Manual IP Routing)

This guide outlines the strict sequence required to deploy the `lecture1` environment without Kubernetes Services. Because Pods receive dynamic IPs upon creation, they must be deployed in dependency order, and their IPs must be manually injected into the configuration files of downstream services.

## Prerequisites
Ensure your Minikube cluster is completely clean (no lingering Pods, PVs, or custom Namespaces). All resources will be deployed to the `default` namespace.

---

## Phase 1: Database Setup
1. **Start Postgres:** 
   Apply the database pod.
   ```bash
   kubectl apply -f lecture1/postgres/pod.yml
   ```
2. **Retrieve the Postgres IP:**
    Wait for the pod to be Running, then fetch its internal IP.
    ```Bash
    kubectl get pod postgres -o wide
    ``` 
    > Note: This database is configured with the credentials yourusername and yourpassword.  

## Phase 2: Message Broker Setup
1. **Patch Kafka for Dynamic IPs:**
    Before applying, modify lecture1/kafka/pod.yml. You must replace the hardcoded KAFKA_ADVERTISED_LISTENERS value with the Downward API to inject its own IP. Add this environment variable:  
    
    ```YAML
            - name: POD_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP
            - name: KAFKA_ADVERTISED_LISTENERS
              value: "PLAINTEXT://$(POD_IP):9092"
    ```
2. Start Kafka and Retrieve IP:
    ``` Bash
    kubectl apply -f lecture1/kafka/pod.yml
    kubectl get pod kafka -o wide
    ```

## Phase 3: Orchestrator & Worker Setup
1. **Configure the Orchestrator:**
    Modify `lecture1/orchestrator/pod.yml`:  
    - Init Container: Change the `pg_isready -h postgres` command to use the Postgres IP.  
    - Database IP: Change `POSTGRES_HOST` to the Postgres IP.  
    - Database Credentials: Change POSTGRES_USER_NAME to yourusername and POSTGRES_USER_PASSWORD to yourpassword to match the DB.  
    - Kafka IP: Change KAFKA_BOOTSTRAP_SERVERS to `<KAFKA_IP>:9092`.
2. **Configure the Worker:**
    - Modify lecture1/worker/pod.yml:  
    - Kafka IP: Change KAFKA_BOOTSTRAP_SERVERS `<KAFKA_IP>:9092`.
3. Deploy Both and Retrieve Orchestrator IP:
    ```Bash
    kubectl apply -f lecture1/orchestrator/pod.yml -f lecture1/worker/pod.yml
    kubectl get pod orchestrator -o wide
    ```

## Phase 4: Frontend Setup
1. **Configure the Frontend:**
    Modify **lecture1/frontend/pod.yml**:  
    - **Orchestrator URL**: Change ORCHESTRATOR_URL to `http://<ORCHESTRATOR_IP>:8080/internal`.  
2. Deploy Frontend:
    ``` Bash
    kubectl apply -f lecture1/frontend/pod.yml
    ```

## Phase 5: Network Access & CORS Configuration
Because the frontend javascript is hardcoded to request data from api.imageprocessing.local, you must map this domain locally and bind it to port 80 to bypass CORS restrictions.

1. **Update Local Hosts File:**
    Add the domain to your machine's /etc/hosts file:
    ```Bash
    sudo nano /etc/hosts
    ```
    Add the following line:
    ``` text
    127.0.0.1   api.imageprocessing.local
    ```
2. **Create a Privileged Port-Forward Tunnel:**
    Forward your local port 80 to the frontend pod's port 8080. Using sudo -E preserves your local Kubernetes credentials.
    ```Bash
    sudo -E kubectl port-forward pod/frontend 80:8080
    ```
    Access the Application:
    Open your browser and navigate to: `http://api.imageprocessing.local`
