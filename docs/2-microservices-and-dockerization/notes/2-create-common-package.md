# 16 Common module extraction (task: 24)

> Continuation of the multi-module restructure started in task 23.
> Prerequisites: task 23 completed, orchestrator running successfully on port 8081.

---

## Context

With the multi-module Maven structure in place, the next step is to extract the classes that will be
shared between `orchestrator` and `gateway` into the `common` module. Without this, both services
would need to duplicate DTOs, enums, and exception classes — which would eventually diverge and
cause contract mismatches between services.

---

## What belongs in `common`

The rule is strict: `common` must have **no Spring dependencies and no framework opinions**.
It is a plain Java library. If a class carries any Spring annotation or references infrastructure
(web, JPA, logging, metrics), it stays in the service that owns it.

**Moves to `common`:**

- DTOs (request and response records): `ImageResponse`, `JobResponse`, `UserResponse`,
  `JobRequest`, `UserRequest`, `ErrorResponse`
- Enums: `JobStatus`, `JobType`
- Plain exception classes: `ResourceNotFoundException`, `InvalidRequestException`,
  `UnsupportedFileFormatException`

**Stays in `orchestrator`:**

- `GlobalExceptionHandler` — carries `@RestControllerAdvice` and Spring Web types
  (`ResponseEntity`, `HttpStatus`, `HttpServletRequest`). Each service handles its own
  HTTP error formatting independently.
- `ObservationConfig`, `AsyncConfig` — Spring `@Configuration` classes, service-specific.
- All entities, repositories, services, controllers, processor, utils — domain logic
  belonging to the orchestrator.

---

## The cross-dependency trap

When moving DTOs, remove any `fromEntity()` static factory methods they contain.
These methods import JPA entity classes, which would create a dependency from `common`
back to `orchestrator` — the opposite of the intended direction. The dependency must
flow only one way: `orchestrator` → `common`.

The mapping responsibility moves to a dedicated `mapper/` package in `orchestrator`,
with one mapper class per entity (`ImageMapper`, `UserMapper`, `JobMapper`). Each mapper
is a pure static utility class with a private constructor and a single `toResponse()` method.

---

## Package structure

Files in `common` use the package root `ch.supsi.imageprocessing.common` with three
sub-packages:

```
common/src/main/java/ch/supsi/imageprocessing/common/
|-- dto/
|-- enums/
`-- exception/
```

The directory path on disk must exactly mirror the package name — Java will not compile
if they differ. This was a source of errors during this task: the directory was initially
created as `supsi/imageprocessing/common/` missing the leading `ch/`.

---

## `common/pom.xml` constraints

The `common` pom must declare only two dependencies:

- `jackson-databind` — for JSON serialization of the record types
- `jakarta.validation-api` — for `@NotNull`, `@NotBlank`, `@Email` on request records

Any Spring dependency added to `common` propagates transitively to every module that
imports it. Adding `spring-boot-starter-webmvc` to `common` (attempted during this task)
caused a `BeanDefinitionOverrideException` in `orchestrator` because it pulled in
`spring-boot-starter-jdbc`, which registered a second `transactionManager` bean
conflicting with the one registered by JPA. The fix was to remove the dependency
from `common` entirely, not to suppress the conflict with
`spring.main.allow-bean-definition-overriding=true`.

---

## Verification steps

After moving files and fixing package declarations:

1. Build `common` in isolation to confirm it has no unresolved dependencies:
   ```
   mvn clean install -pl common --also-make
   ```
2. Build and run `orchestrator` to confirm imports resolve correctly and the application
   starts against the local PostgreSQL instance:
   ```
   mvn spring-boot:run -pl orchestrator
   ```
