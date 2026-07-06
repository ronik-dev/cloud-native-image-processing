## 6. Layer 7 Routing: NGINX Ingress Controller

### Context & Decision
To expose the Cloud-Native Image Processing application to external clients, a robust Layer 7 load balancing solution is required. Relying on `NodePort` or temporary port-forwarding is insufficient for a production-grade architecture. 

**Decision:** Implement the **NGINX Ingress Controller**. NGINX is the industry standard for Kubernetes ingress, providing advanced HTTP routing, host-based mapping, and seamless integration with the Spring Boot API Gateway.

### Implementation Steps

#### 1. Provisioning the Ingress Engine
With the Minikube cluster fully operational, the native NGINX ingress controller was enabled via Minikube's built-in addon manager. This automatically provisions the required NGINX proxy pods and load balancers in the `ingress-nginx` namespace to actively monitor the cluster for routing rules.

```bash
minikube addons enable ingress
```

#### 2. Defining the Ingress Routing Blueprint
Instead of relying on raw YAML dumps, the `Ingress` resource was explicitly configured with the following routing logic to bridge external traffic to the internal cluster network:

* **Ingress Class (`ingressClassName: nginx`):** Explicitly declared so that the NGINX controller knows it is responsible for managing this specific set of rules.
* **Host Mapping (`host: api.imageprocessing.local`):** Instructs the load balancer to only intercept requests specifically requesting this domain name.
* **Path Routing (`pathType: Prefix`, `path: /`):** Configures a catch-all route, meaning any URL path appended to the domain will be handled by this rule.
* **Backend Target (`gateway-service:8080`):** Directs all successfully intercepted and matched traffic to the Spring Boot API Gateway's internal Kubernetes Service on port 8080.

#### 3. Simulating Local DNS Resolution
In a real-world production environment, a public DNS server (like AWS Route53 or Cloudflare) translates a domain name into a server's IP address. Because this is a local development environment, the host operating system (Arch Linux) does not inherently know what `api.imageprocessing.local` is. 

To bridge this gap, the host machine's local `/etc/hosts` file was appended with a direct map between the Minikube virtual IP and the application's domain name:

**Appended mapping:** `192.168.49.2    api.imageprocessing.local`

**Why this was necessary:** By hardcoding this entry, we forcefully instruct the operating system's internal DNS resolver to bypass the internet. When a developer or external tool queries `api.imageprocessing.local`, the OS immediately forwards the traffic directly to the Minikube cluster IP (`192.168.49.2`), perfectly simulating a real-world DNS "A Record" without requiring an actual DNS server.

#### 4. Architecture Verification
The end-to-end routing architecture was validated by simulating an external client request to the custom domain. The NGINX proxy successfully intercepted the host header and established a connection with the Spring Boot gateway pod.

```bash
curl -I [http://api.imageprocessing.local](http://api.imageprocessing.local)
```

**Result:** `HTTP/1.1 200 OK` *(Traffic successfully routed to the application layer).*
