# 4 Update the gateway to handle auth
> **Context:** With Keycloak deployed, the Gateway must be updated to act as an OAuth2 Client using the Backend-For-Frontend (BFF) pattern. The frontend becomes a dumb terminal that relies on secure HTTP-only session cookies managed by the Gateway.

### 1. Spring Security & BFF Pattern
The Gateway was heavily refactored to include `spring-boot-starter-oauth2-client` and `spring-boot-starter-security`.
*   **Configuration (`SecurityConfig.java`):** All routes are secured (except `/actuator/health`) using `.oauth2Login()`. 
*   **CSRF Disabled:** Because the frontend is static HTML/JS with no templating engine to easily inject CSRF tokens, `.csrf(csrf -> csrf.disable())` is explicitly configured.
*   **Custom Logout:** Keycloak requires an RP-Initiated logout. A custom `LogoutSuccessHandler` was built to append the `post_logout_redirect_uri` (and the required `id_token_hint`) so users are cleanly routed back to the frontend after terminating their Keycloak session.

### 2. Identity Bridging (`MeController`)
Keycloak owns *who can log in*, but the Orchestrator owns *who owns which images*. 
*   The `MeController` was introduced to bridge these domains.
*   When the frontend calls `GET /api/me`, the Gateway extracts the `preferred_username` and `email` from the OIDC token (`@AuthenticationPrincipal OidcUser`) and synchronously calls the Orchestrator's new `/users/find-or-create` endpoint. 

### 3. Frontend Session Expiration Handling
Since the Gateway now intercepts unauthenticated requests with a `302 Redirect` to the Keycloak login screen, the frontend's `fetch()` API would silently follow this redirect and fail when trying to parse the resulting HTML login page as JSON.
*   **Fix:** `script.js` was updated to inspect the `Content-Type` header on `loadCurrentUser()` and `refreshImagesAndJobs()`. If `text/html` is detected, the app automatically executes `window.location.href = '/'` to cleanly trigger the browser's login flow.

