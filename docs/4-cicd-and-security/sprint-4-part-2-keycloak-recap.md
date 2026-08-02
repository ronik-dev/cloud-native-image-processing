# Sprint 4 : Authentication & Security Recap

**Cloud-Native Image Processing : Auth & Security Phase** _Romano Nicola . SUPSI DTI-ISIN . August 2026_

> This document consolidates the integration of Keycloak as an Identity Provider (IdP) and the transition of the API Gateway to a Backend-For-Frontend (BFF) OAuth2 client.

## Table of Contents
[[ _TOC_ ]]

## 1. Architecture Overview
Sprint 4 decouples user identity management from the core domain. Authentication is outsourced to Keycloak, while the Gateway acts as the sole security boundary, enforcing OIDC flows and managing secure HTTP-only sessions for the static frontend. The Orchestrator trusts the Gateway implicitly and handles users purely as logical owners of images and jobs.

```mermaid
flowchart TD
    Client[Browser] -->|1. HTTP GET /| GW[Gateway]
    GW -.->|2. 302 Redirect| KC[Keycloak IdP]
    Client -->|3. Authenticate| KC
    KC -.->|4. Auth Code| GW
    GW -->|5. Swap Code for Token| KC
    GW -->|6. Set Session Cookie| Client
    Client -->|7. API Request + Cookie| GW
    GW -->|8. Forward Request (No JWT)| ORC[Orchestrator]
```

## 2. Keycloak Infrastructure
Keycloak is deployed natively within the `imageprocessing` namespace alongside the existing microservices.

*   **Database Integration:** It relies on the existing `postgres` StatefulSet.
*   **Automated Realm Import:** A ConfigMap (`keycloak-realm-config`) mounts `imageprocessing-realm.json` directly into Keycloak's `/opt/keycloak/data/import` directory, automating the creation of the realm and the `gateway` client.

## 3. The Gateway as a BFF (Backend-For-Frontend)
The `gateway` service no longer blindly routes traffic; it actively manages the OAuth2 Authorization Code flow.

### 3.1 Security Configuration
*   **Session Management:** The frontend contains no OAuth2 logic or JWT libraries. The Gateway retains the tokens server-side and issues a session cookie to the browser.
*   **CSRF Deliberate Tradeoff:** CSRF protection is disabled in `SecurityConfig.java`.
*   **RP-Initiated Logout:** Spring's default OIDC logout was replaced with a custom `LogoutSuccessHandler` that explicitly builds the redirect URI containing `post_logout_redirect_uri` and `id_token_hint` to satisfy Keycloak's strict logout spec.

### 3.2 Bridging Identity
Keycloak manages authentication, but the Orchestrator needs user records to maintain foreign key relationships for `Image` and `ProcessingJob` tables.
*   The Gateway exposes `GET /api/me`, extracting `preferred_username` and `email` from the Spring `OidcUser` principal.
*   It calls the Orchestrator's `POST /users/find-or-create` endpoint.

## 4. Orchestrator Updates & Event-Driven Deletion
The Orchestrator's user management was streamlined to reflect its new role as a downstream service.

### 4.1 Strict Data Validation
The `UserService.findOrCreateUser` method enforces strict constraints, throwing an `InvalidRequestException` if either the `username` or `email` is null or blank. This ensures data integrity but requires Keycloak users to have an email address populated to successfully access the application.

### 4.2 Event-Driven User Deletion (Kafka)
Synchronous REST deletion (`DELETE /api/users/{id}`) was replaced with a reactive, event-driven pattern.
*   The Orchestrator now implements a `UserEventListener` annotated with `@KafkaListener(topics = "${kafka.topics.user-events}")`.
*   If an administrator deletes a user centrally from Keycloak, a `DELETE` event is published to the `user.events` Kafka topic.
*   The Orchestrator consumes this event and executes `userService.deleteByUsername()`, triggering the JPA cascade-delete for all associated images, jobs, and physical storage files.

## 5. Sprint Completion Status (Auth & Security)

| Task | Acceptance Criterion | Status | Notes |
|---|---|---|---|
| Deploy Keycloak | IdP running in K8s, backed by Postgres | Done | Init Job used to bootstrap DB schema securely |
| Automated Realm Config | Realm and Client configure automatically | Done | ConfigMap mounted as read-only import |
| Gateway BFF | Spring Security OAuth2 Client implemented | Done | JWTs remain in Gateway; session cookies sent to UI |
| UI Session Handling | UI catches expired sessions and redirects | Done | `Content-Type` checked for `text/html` in `script.js` |
| Identity Bridging | Keycloak token maps to Orchestrator User | Done | `MeController` + `find-or-create` endpoint implemented |
| Event-Driven Deletion | Keycloak user deletion cascades to backend | Done | Orchestrator listens to `user.events` Kafka topic |
