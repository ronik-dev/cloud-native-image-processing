# Devlog: Task Add Keycloak
> **Context:** The system required a centralized Identity Provider (IdP) to handle user authentication, decoupling password management and identity flows from our domain logic. Keycloak was selected for its native OIDC support and Kubernetes-friendly deployment model.

### 1. Kubernetes Deployment & Stateful Backing
Keycloak is deployed as a stateless `Deployment` (`keycloak/deployment.yml`) backed by a persistent PostgreSQL database. 
*   **Database Initialization:** Instead of relying on manual DB provisioning, a Kubernetes `Job` (`keycloak-create-db`) handles the creation of the `keycloak` database and its owner role in a strictly idempotent way using `pg_isready` and `psql` scripts.
*   **Init Container:** The Keycloak pod uses an `initContainer` to wait for the Postgres database to become fully available before attempting to start the Keycloak server.

### 2. Realm and Client Configuration
Configuration is injected automatically via a realm import file mounted from a ConfigMap (`keycloak-realm-config`).
*   **Realm:** `imageprocessing`.
*   **Client:** `gateway` (configured for OIDC `authorization_code` flow, `publicClient: false`, and `client-secret` authentication).
*   **Security Tradeoff:** Since this is an internal thesis project, `sslRequired` is set to `none` to simplify the local Minikube routing without requiring TLS certificate provisioning for internal pod-to-pod traffic.

### 3. Bootstrap Admin Caveat
Keycloak 26+ uses `KC_BOOTSTRAP_ADMIN_USERNAME` and `PASSWORD` to create the initial admin account. However, as documented in `secret.yml.example`, these variables *only* take effect when Keycloak initializes an empty database for the first time. Subsequent changes to these secrets require manual admin creation via the UI, as the DB is persistent.

