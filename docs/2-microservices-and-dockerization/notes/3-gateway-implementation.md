# 17 Gateway service implementation (task: 34)

> Prerequisites: task 23 (repo restructure) and task 24 (common module) completed.
> Orchestrator running on port 8081 with `/internal` context path.

---

## Context

With the orchestrator exposing an internal domain API, the next step is to implement the
**gateway service** — the only service the browser communicates with directly. The gateway
sits in front of the orchestrator, forwards all requests to it via HTTP, and serves the
dashboard UI. It owns no database, no entities, and no domain logic.

This service implements the **Backend For Frontend (BFF)** pattern: it presents a
public-facing API shaped for the UI while shielding the internal orchestrator from direct
external access. It also acts as a **security boundary** — in later phases, JWT validation
will happen here, and the orchestrator will trust only internal calls.

---

## Architecture

```
Browser
  |
  | HTTP (port 8080)
  v
Gateway  (/api/*)
  |
  | HTTP (port 8081/internal/*)
  v
Orchestrator
  |
  v
PostgreSQL
```

All external traffic enters through the gateway. The orchestrator is never called directly
by the browser. The gateway has no knowledge of the database schema — it only knows the
orchestrator's HTTP contract.

---

## Technology choices

**Spring Boot + Spring Web MVC** — the gateway runs on Tomcat with standard servlet-based
controllers, the same stack as the orchestrator. This keeps the deployment model uniform
and simplifies debugging.

**WebClient (Spring WebFlux)** — used as the HTTP client to call the orchestrator.
`WebClient` is the modern Spring HTTP client; `RestTemplate` is in maintenance mode and
discouraged for new code. `WebClient` is designed for reactive, non-blocking code but can
be used in a blocking servlet context by calling `.block()` on each response, which is
the approach taken here.

The tradeoff of `.block()` is that the thread blocks while waiting for the orchestrator
response, losing the non-blocking benefit. This is acceptable for this project since the
bottleneck is the AI worker doing CPU-heavy processing, not thread availability in the
gateway. The advantage of `WebClient` over `RestTemplate` is that if the gateway
controllers are ever migrated to return `Mono` (reactive), the `.block()` calls can be
removed with no other changes to the client code.

**`ParameterizedTypeReference`** — used for deserializing list responses (`List<ImageResponse>`,
`List<UserResponse>`, `List<JobResponse>`). `bodyToFlux` was initially used but proved
unreliable with empty arrays. `bodyToMono` with `ParameterizedTypeReference` is the
correct pattern for collecting a typed list from a JSON array response.

---

## Internal API contract

The gateway communicates with the orchestrator over a defined internal contract.
All orchestrator endpoints are prefixed with `/internal` via Spring Boot's
`server.servlet.context-path` property — this keeps the prefix out of every
`@RequestMapping` annotation and applies it uniformly at the server level.

The gateway's `OrchestratorClient` maps public `/api/*` paths to internal `/users`,
`/images`, `/jobs` paths. The base URL `http://localhost:8081/internal` is configured
via the `orchestrator.url` property, read from environment variable with a local default,
so no code changes are needed when moving to Docker or Kubernetes.

---

## Service decomposition decision: Option B

During planning, two decomposition options were considered:

**Option A** — controllers move to gateway, orchestrator keeps `/api/*` paths unchanged.
Simple but makes no architectural distinction between the public and internal APIs.

**Option B** — orchestrator exposes a leaner `/internal/*` API without UI concerns;
gateway exposes the public `/api/*` API and owns the browser-facing contract.

Option B was chosen because it creates a genuine architectural boundary: the orchestrator
is a pure domain service with no awareness of how responses are presented to users.
UI concerns such as `Location` headers (previously returned by the orchestrator's upload
endpoint) moved to the gateway. The orchestrator's `uploadFile` endpoint was simplified
to return an `ImageResponse` body directly instead of a redirect URI — the gateway
decides what the browser receives.

---

## Error propagation

By default `WebClient` throws a `WebClientResponseException` when it receives a 4xx or
5xx response from the orchestrator. Without handling this, all upstream errors arrive at
the browser as 500.

The solution has two parts:

A `defaultStatusHandler` in `WebClientConfig` intercepts error responses from the
orchestrator, reads the raw response body as a `String`, and wraps it in a
`WebClientResponseException` preserving the original status code and body bytes. Reading
the body as raw `String` rather than deserializing it directly is important —
`WebClient`'s internal `ObjectMapper` does not share the Spring-managed instance and
would fail on certain field types. The raw bytes are attached to the exception so the
handler can access them later.

A `GlobalExceptionHandler` in the gateway catches `WebClientResponseException`,
deserializes the raw body into `ErrorResponse` using the Spring-managed `ObjectMapper`,
re-stamps the `path` field with the gateway's request URI (not the internal orchestrator
path), and forwards the error to the browser with the original status code. If
deserialization fails (empty body or unexpected response shape), a generic fallback
error is returned.

This works cleanly because `ErrorResponse` uses `String` for the timestamp field
rather than `LocalDateTime` — a deliberate design decision to avoid a `JavaTimeModule`
dependency in the gateway. The orchestrator serializes `LocalDateTime.now().toString()`
before constructing the response, producing an ISO string that is JSON-serializable with
a plain `ObjectMapper` and equally readable to the browser.

This means a 404 from the orchestrator arrives at the browser as a 404, a 409 as a 409,
and so on — the gateway is transparent for errors as well as successes.

---

## Input validation

The gateway validates inputs at the edge before forwarding to the orchestrator:

- `@Validated` at the controller class level activates constraint checking on path variables.
- `@Min(0)` on all `Long id` path variables rejects negative IDs immediately without a
  network call.
- `@Valid` on request body parameters (`UserRequest`, `JobRequest`) validates the payload
  structure before forwarding.

The orchestrator performs the same validation independently as a second line of defence.
This double validation is intentional — the gateway catches obviously malformed requests
cheaply, while the orchestrator remains the authority on domain correctness.

The gateway `GlobalExceptionHandler` also handles `ConstraintViolationException` and
`MethodArgumentNotValidException` to return clean 400 responses for validation failures
at the gateway level.

---

## File upload and download

These two endpoints required special handling beyond simple JSON proxying.

**Upload (`POST /api/images`)** — the gateway receives a `multipart/form-data` request
from the browser containing the image file and a `userId` parameter. It reconstructs a
`MultipartBodyBuilder` payload and forwards it to the orchestrator as a new multipart
request via `WebClient`. The file bytes are read into memory (`file.getBytes()`) which
is acceptable for image-sized files. An `IOException` during byte reading is wrapped in
`UncheckedIOException` and handled by the `GlobalExceptionHandler`, which returns a
clean 500 response.

**Download (`GET /api/jobs/{id}/result`)** — the gateway fetches the binary response
from the orchestrator as `byte[]` using `WebClient`'s `toEntity(byte[].class)`, which
also captures the response headers. The `Content-Disposition` header (containing the
output filename) is forwarded to the browser alongside the binary body. Loading the
file as `byte[]` is sufficient for images; streaming with `InputStream` would be needed
only for very large files.

---

## UI ownership

The static frontend (`index.html`, `script.js`, `style.css`) was moved from the
orchestrator to `gateway/src/main/resources/static/`. The orchestrator's static folder
was deleted — it serves no UI. All API calls in `script.js` were updated to target
port 8080 (the gateway) instead of port 8081 (the orchestrator).

This enforces the architectural principle: the browser knows only about the gateway.

---

## Local development

Both services must be running simultaneously. The gateway requires the orchestrator to
be up before it can serve any requests, but it starts independently regardless.

| Service      | Port | Command                                |
|--------------|------|----------------------------------------|
| Orchestrator | 8081 | `mvn spring-boot:run -pl orchestrator` |
| Gateway      | 8080 | `mvn spring-boot:run -pl gateway`      |

The orchestrator URL is configured in `gateway/src/main/resources/application.properties`
and reads from the `ORCHESTRATOR_URL` environment variable with `http://localhost:8081/internal`
as the local default. No `.env` file is required for the gateway in local development.

