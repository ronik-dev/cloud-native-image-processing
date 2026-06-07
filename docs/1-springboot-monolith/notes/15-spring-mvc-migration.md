# 15 Architectural Evolution to Spring MVC (task: 22)
> This guide is OS specific for Arch Linux, as this project is developed on this os
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> These guide assumes you have an internet connection pacman and yay working and installed.

### Context
The initial development phase utilized Spring Data REST to rapidly prototype the domain model and expose a hypermedia-driven `HATEOAS/HAL` API. As the platform matures toward a microservices architecture and integrates asynchronous processing pipelines, the API contract must be strictly decoupled from the underlying database schema.

This refactoring removes the automated Data REST generation and transitions the application to a pure Spring Web MVC architecture. By explicitly mapping database entities to immutable Data Transfer Objects `DTOs` within dedicated controllers, the system prevents transaction context leaks `such as LazyInitializationException`, ensures complete compatibility with the centralized exception handler, and establishes a robust, predictable contract for frontend UI integration.

### Setup and Changes Made
##### 1. Maven Dependency Cleanup
The automated REST generation tools were removed from the project build configuration. The spring-boot-starter-data-rest and spring-data-rest-hal-explorer dependencies were deleted from the pom.xml to prevent the framework from auto-generating HAL-compliant endpoint bindings over the repository layer.

##### 2. Repository Simplification
The database repository layer was completely stripped of web-facing directives. The @RepositoryRestResource annotation was removed from the ImageRepository, reverting it to a standard, internal Spring Data JPA repository that no longer dictates JSON serialization logic to the client.

##### 3. Removal of Projections
Because the API no longer relies on Spring Data REST to shape and format the HTTP response payloads, the custom projection interfaces `ImageSummary and JobSummary` became obsolete. The entire projection package and its contents were deleted from the source tree to eliminate unused code and technical debt.

##### 4. Controller Explicit Routing
To restore the foundational read capabilities previously handled by the automated HAL endpoints, explicit endpoint methods were added to the ImageController. These standard @GetMapping methods manually retrieve the persistent entities and map them directly into the predefined ImageResponse Java Records before returning the HTTP payload, granting absolute programmatic control over the data structure transmitted to the client.

##### 5. API Surface Reduction and Security Preparation
Endpoints were audited to reduce the attack surface and eliminate unnecessary database loads (YAGNI principle).
Bulk Retrieval Dropped: The `GET /api/users/{id}/images` endpoint was removed entirely. Deferring the implementation of a gallery view prevents potential JVM memory exhaustion and sidesteps N+1 query optimization until the UI explicitly demands it.
Identity Technical Debt Documented: The explicit `@RequestParam("userId")` in the upload payload was retained temporarily to satisfy database constraints. It has been formally documented in the codebase as technical debt (Insecure Direct Object Reference) to be removed in Phase 3.4.3, at which point identity will be securely extracted server-side from the Keycloak JWT.
