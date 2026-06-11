# 15 Microservices repository setup (task: 23)

> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes you have an internet connection, pacman and yay working and installed.

---

## Context

At this point we have the working Spring Boot monolith at the base of the git repository.
The goal is to restructure the repository to prepare the development of the various microservices.
Everything will run locally; containerization will be handled in the future.

The target structure introduces a **Maven multi-module layout** alongside a standalone Python project for the AI worker:

```
cloud-native-image-processing/
|-- pom.xml          <- parent pom, declares all Java modules
|-- common/          <- shared DTOs, enums, exceptions
|-- orchestrator/    <- domain core: entities, repositories, job logic
|-- gateway/         <- BFF: UI, controllers, HTTP client to orchestrator
`-- worker/          <- Python FastAPI AI worker (ffmpeg + HuggingFace)
```

---

## Maven Multi-Module Structure

### What is a Maven multi-module project?

A standard Maven project has a single `pom.xml` that describes one artifact — one JAR or WAR. A multi-module project introduces a **parent `pom.xml`** at the repository root whose only job is to declare which sub-projects (modules) exist and to share common configuration across all of them. Each module is a normal Maven project with its own `pom.xml`, but instead of inheriting from Spring Boot directly, it inherits from the parent.

The parent itself inherits from `spring-boot-starter-parent`, so the full inheritance chain is:

```
spring-boot-starter-parent
        ↑
cloud-native-image-processing  (parent pom, packaging=pom)
        ↑                ↑                ↑
    common          orchestrator       gateway
```

This means dependency versions, Java version, compiler settings, and plugin configuration are declared once in `spring-boot-starter-parent` and flow down to every module automatically. No module needs to repeat `<java.version>21</java.version>` or specify a version for Spring Boot dependencies.

### What each pom does

**Parent `pom.xml`** — lives at the repo root. Sets `packaging=pom` (meaning it produces no artifact itself) and declares the three modules. Any property or dependency management defined here is inherited by all children.

**`common/pom.xml`** — declares a plain JAR module with no Spring Boot plugin. It only needs Jackson for JSON serialization and the Jakarta Validation API for `@NotNull` and similar annotations on the DTO fields. It has no `main` class and cannot be run — it exists purely to be imported by the other modules. It does not include the Spring Boot repackage plugin, so its JAR is a standard library JAR rather than a fat executable JAR.

**`orchestrator/pom.xml`** — declares the domain service. Its parent is the project parent (not Spring Boot directly). It declares a dependency on `common`, which gives it access to all shared DTOs and exceptions. It keeps all the JPA, PostgreSQL, web, actuator, and validation dependencies since it owns the database and the domain logic. It includes the Spring Boot Maven plugin so it produces a runnable fat JAR.

**`gateway/pom.xml`** — declares the BFF service. Also inherits from the project parent and depends on `common`. It intentionally has no JPA or datasource dependency — it has no database. Currently empty of source code; it will be populated during the decomposition work.

### What `mvn clean package` does in this setup

When run from the repo root, Maven reads the parent pom, resolves the module list, and builds them in **dependency order** — called the reactor build order. Because `common` has no dependencies on the other modules, it builds first. `orchestrator` and `gateway` both depend on `common`, so they build after it. The result is that when `orchestrator` compiles, the `common` JAR is already available in the local Maven repository.

This is why the build output shows:

```
common       -> SUCCESS  (built first)
orchestrator -> SUCCESS  (found common)
gateway      -> SUCCESS  (found common, empty JAR warning expected)
```

The warning on `gateway` — *JAR will be empty* — is expected at this stage since no source files have been moved there yet. It will disappear once the gateway source code is populated.

---

## Steps

### 1. Commit the monolith snapshot

Before restructuring anything, commit the current state cleanly. This provides a safe rollback point and makes the Git history tell the architectural story.

```bash
git add .
git commit -m "chore: snapshot monolith before multi-module restructure"
```

### 2. Move the current project to the orchestrator module

Create the `orchestrator/` folder and move the existing `src/` directory and `pom.xml` there. The monolith code and logic will live in this module, as it owns the domain model, repositories, and job orchestration.

```bash
mkdir orchestrator
mv src orchestrator/
mv pom.xml orchestrator/
```

### 3. Create the parent `pom.xml`

Create a new `pom.xml` at the repo root. It sets `packaging=pom`, inherits from `spring-boot-starter-parent`, and declares `common`, `orchestrator`, and `gateway` as modules.

Key points:
- `<packaging>pom</packaging>` — this is mandatory; without it Maven treats the root as a regular JAR project.
- No `<build>` or `<dependencies>` sections needed here — those belong in the child modules.
- The `<modules>` order does not need to reflect the build order; Maven resolves that automatically from the dependency graph.

### 4. Update the orchestrator `pom.xml`

Edit `orchestrator/pom.xml` to point its `<parent>` at the new project parent instead of `spring-boot-starter-parent` directly. Remove the redundant `<groupId>`, `<version>`, and `<properties>` blocks that are now inherited. Add the dependency on `common`.

Remove these non-existent test dependencies if present — they will cause a build failure:
- `spring-boot-starter-data-jpa-test`
- `spring-boot-starter-data-rest-test`
- `spring-boot-starter-webmvc-test`

`spring-boot-starter-test` already provides JUnit 5, Mockito, MockMvc, and `@DataJpaTest` support.

### 5. Create the `common` module

```bash
mkdir -p common/src/main/java/ch/supsi/imageprocessing/common
```

Create `common/pom.xml` inheriting from the project parent. Dependencies: `jackson-databind` and `jakarta.validation-api` only. No Spring Boot plugin.

Move the following from the monolith into `common/src/`:
- All DTO records: `ImageResponse`, `JobResponse`, `UserResponse`, `JobRequest`, `UserRequest`, `ErrorResponse`
- Enums: `JobStatus`, `JobType`
- Exception classes: `ResourceNotFoundException`, `InvalidRequestException`, `UnsupportedFileFormatException`

Update the package declaration in each moved file to `ch.supsi.imageprocessing.common` (or keep a consistent sub-package of your choice) and fix imports in `orchestrator` accordingly.

Verify common builds in isolation:

```bash
mvn clean install -pl common
```

### 6. Create the `gateway` module

```bash
mkdir -p gateway/src/main/java/ch/supsi/imageprocessing/gateway
mkdir -p gateway/src/main/resources
```

Create `gateway/pom.xml` inheriting from the project parent. Dependencies: `spring-boot-starter-web` and `common`. No JPA, no datasource.

Create a minimal main class and `application.properties` with `server.port=8080`. Verify the empty application starts:

```bash
mvn clean package -pl gateway
```

The *JAR will be empty* warning disappears once source files are added.

### 7. Create the `worker` Python project

Create the `worker/` directory at the repo root. This project is entirely independent of Maven — it is a Python service that will be built and run separately.

```bash
mkdir worker
cd worker
```

Install `uv` (Python package manager):

```bash
sudo pacman -S uv
```

Initialise the project and install dependencies:

```bash
uv init
uv add fastapi
uv add uvicorn
uv add ffmpeg-python
```

> **Note on ffmpeg-python:** this library provides a fluent Python API over the FFmpeg binary, replacing raw `subprocess` calls. It must still be available as a system dependency:
> ```bash
> sudo pacman -S ffmpeg
> ```

Create a stub `main.py` with an empty FastAPI app to verify the setup:

```bash
uv run uvicorn main:app --port 8082
```

### 8. Verify the full build

From the repo root, run the full reactor build:

```bash
mvn clean package
```

Expected output:

```
cloud-native-image-processing  SUCCESS
common                         SUCCESS
orchestrator                   SUCCESS
gateway                        SUCCESS  (empty JAR warning is normal)
```

---

## Local Development — Running All Services

At this stage services are run directly on the host, with no Docker involved. Each requires its own terminal.

| Service      | Command                                      | Port  |
|--------------|----------------------------------------------|-------|
| PostgreSQL   | managed by systemctl (already running)       | 5432  |
| Orchestrator | `mvn spring-boot:run -pl orchestrator`       | 8081  |
| Gateway      | `mvn spring-boot:run -pl gateway`            | 8080  |
| Worker       | `cd worker && uv run uvicorn main:app --port 8082` | 8082 |

Service URLs are read from environment variables so they can be changed to Docker service names later without touching code:

| Variable            | Value for local dev             |
|---------------------|---------------------------------|
| `IMAGE_SERVICE_URL` | `http://localhost:8081`         |
| `AI_WORKER_URL`     | `http://localhost:8082`         |
| `STORAGE_DATA_DIR`  | `/tmp/imageprocessing/data`     |

---

## Repository Structure After Restructure

```
cloud-native-image-processing/
|-- pom.xml                          <- parent pom
|-- common/
|   |-- pom.xml
|   `-- src/main/java/ch/supsi/imageprocessing/common/
|       |-- dto/
|       |   |-- ImageResponse.java
|       |   |-- JobRequest.java
|       |   |-- JobResponse.java
|       |   |-- UserRequest.java
|       |   |-- UserResponse.java
|       |   `-- ErrorResponse.java
|       |-- enums/
|       |   |-- JobStatus.java
|       |   `-- JobType.java
|       `-- exception/
|           |-- ResourceNotFoundException.java
|           |-- InvalidRequestException.java
|           `-- UnsupportedFileFormatException.java
|-- orchestrator/
|   |-- pom.xml
|   `-- src/
|       `-- main/java/ch/supsi/imageprocessing/
|           |-- config/
|           |-- controller/
|           |-- entity/
|           |-- repository/
|           |-- service/
|           |-- processor/
|           `-- utils/
|-- gateway/
|   |-- pom.xml
|   `-- src/main/java/ch/supsi/imageprocessing/gateway/
|       `-- GatewayApplication.java
|-- worker/
|   |-- pyproject.toml
|   |-- requirements.txt
|   `-- main.py
|-- docs/
|-- .gitlab-ci.yml
|-- .gitignore
`-- README.md
```
