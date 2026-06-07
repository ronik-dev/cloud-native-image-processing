# Sprint 1 : Architecture & Data Model Documentation

**Cloud-Native Image Processing : Monolith Phase** _Romano Nicola . SUPSI DTI-ISIN . June 2026_

---

## Table of Contents

[[#1. Architecture Overview]]
[[#2. Local Development Environment]]
[[#3. Entity-Relationship Model]]
[[#4. Processing Pipeline]]
[[#5 REST API Reference]]
[[#6. Cross-Cutting Design Decisions]]
[[#7.Sprint 1 Completion Status]]

---

## 1. Architecture Overview

The first sprint delivers a Spring Boot monolith that implements the full image-processing pipeline in a single deployable artifact. Starting with a monolith is deliberate: it establishes a working, testable baseline for all domain logic, data contracts, and API surfaces before complexity is introduced by distribution. The monolith is designed from the outset to be decomposed into microservices in a later sprint, so internal boundaries between concerns are respected even within a single process.

### 1.1 Technology Stack

| Layer            | Technology                  | Version / Notes                                 |
| ---------------- | --------------------------- | ----------------------------------------------- |
| Runtime          | OpenJDK                     | 21 (LTS)                                        |
| Framework        | Spring Boot                 | 4.0.6                                           |
| Persistence      | Spring Data JPA / Hibernate | via `spring-boot-starter-data-jpa`              |
| Database         | PostgreSQL                  | 18.4 (installed locally via pacman)             |
| Web tier         | Spring Web MVC              | `spring-boot-starter-webmvc`                    |
| Build            | Apache Maven                | 3.9.16                                          |
| Image processing | FFmpeg                      | System dependency, invoked via `ProcessBuilder` |
| Observability    | Micrometer / `@Observed`    | `spring-boot-starter-actuator`                  |
| Testing          | JUnit 5 + Mockito + H2      | H2 in-memory DB for unit/integration tests      |

> **Note on infrastructure:** all dependencies (PostgreSQL, FFmpeg, Java, Maven) are installed directly on the developer's machine. No Docker or containerisation is used in this sprint. See [[#2. Local Development Environment|Section 2]]) for setup details.

### 1.2 Architectural Desing

The architecture follows a classic layered model within the single process: a web/controller tier, a service/business-logic tier, and a persistence/repository tier. Two deliberate departures from the simplest possible approach are worth documenting.

**HAL/Spring Data REST → Spring MVC.** Spring Data REST was used in early prototyping to expose HATEOAS endpoints rapidly and explore the domain model. It was subsequently removed in favour of explicit Spring MVC controllers. This transition decouples the public API contract from the database schema, eliminates `LazyInitializationException` risks from open-session patterns, and produces a more predictable surface for future microservice decomposition.

**`ImageService` vs `ImageProcessor` separation.** The processing component is intentionally split in two: `ImageService` owns transactional state (job lifecycle, database writes), while `ImageProcessor` owns physical I/O (FFmpeg invocation, file streaming). This mirrors the boundary that will exist between a Job Orchestrator microservice and a Worker microservice in Sprint 2 : the `@Async` boundary here becomes a message-queue boundary there, with minimal changes to the orchestration logic.

### 1.3 Package Structure

```
ch.supsi.imageprocessing/
|- controller/    -> REST endpoints (UserController, ImageController, ProcessingJobController)
|- service/       -> Business logic (UserService, ImageService, ProcessingJobService, StorageService)
|- processor/     -> Physical I/O worker (ImageProcessor)
|- entity/        -> JPA entities (User, Image, ProcessingJob) + enums (JobType, JobStatus)
|- repository/    -> Spring Data JPA repositories
|- dto/           -> Immutable records for API I/O (ImageResponse, JobResponse, UserResponse, …)
|- exception/     -> Domain exceptions + GlobalExceptionHandler
|- utils/         -> ImageFormatValidator
```

---

## 2. Local Development Environment

All tools are installed directly on the developer's machine (Arch Linux). The steps below reflect the actual setup used in this project; for other operating systems the same tools apply but installation commands differ.

### 2.1 Java 21

```bash
sudo pacman -S jdk21-openjdk
java -version
# openjdk version "21.0.11" 2026-04-21
```

### 2.2 Apache Maven 3.9

```bash
sudo pacman -S maven
mvn -version
# Apache Maven 3.9.16
# Java version: 21.0.11, vendor: Arch Linux
```

### 2.3 PostgreSQL 18

PostgreSQL is installed and managed as a local system service : no Docker is involved.

```bash
sudo pacman -S postgresql

# Initialise the data directory
sudo mkdir -p /var/lib/postgres
sudo chown -R postgres:postgres /var/lib/postgres
sudo -u postgres initdb -D /var/lib/postgres/data

# Enable and start
sudo systemctl enable --now postgresql

# Create application user and database
sudo -i -u postgres psql
```

```sql
CREATE USER my_app_user WITH PASSWORD 'my_secure_password';
CREATE DATABASE cloud_native_db OWNER my_app_user;
```

Credentials are stored in a `.env` file at the project root (git-ignored) and loaded at runtime via the [`spring-dotenv`](https://github.com/paulschwarz/spring-dotenv) library:

```
POSTGRES_USER_NAME=my_app_user
POSTGRES_USER_PASSWORD=my_secure_password
POSTGRES_DB_NAME=cloud_native_db
```

`application.properties` references these variables:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/${POSTGRES_DB_NAME:cloud_native_db}
spring.datasource.username=${POSTGRES_USER_NAME}
spring.datasource.password=${POSTGRES_USER_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
```

### 2.4 FFmpeg

FFmpeg must be available on the system `PATH` as it is invoked by `ImageProcessor` via `ProcessBuilder`.

```bash
sudo pacman -S ffmpeg
ffmpeg -version
```

### 2.5 Running the Application

```bash
mvn clean spring-boot:run
```

Hibernate creates or updates the schema on startup (`ddl-auto=update`). The API is available at `http://localhost:8080/api`.

---

## 3. Entity-Relationship Model

The data model consists of three entities with two one-to-many relationships.

### 3.1 Entities

#### `users`

| Column     | Type      | Constraints      | Notes              |
| ---------- | --------- | ---------------- | ------------------ |
| `id`       | `BIGINT`  | PK, AUTO         | Identity-generated |
| `username` | `VARCHAR` | NOT NULL, UNIQUE | Login identifier   |
| `email`    | `VARCHAR` | NOT NULL, UNIQUE | Contact address    |

#### `image`

| Column       | Type        | Constraints                      | Notes                                     |
| ------------ | ----------- | -------------------------------- | ----------------------------------------- |
| `id`         | `BIGINT`    | PK, AUTO                         | Identity-generated                        |
| `name`       | `VARCHAR`   | NOT NULL                         | Original filename as uploaded             |
| `format`     | `VARCHAR`   | NOT NULL                         | Detected format (`png`, `jpg`, `webp`, …) |
| `storageKey` | `VARCHAR`   | NOT NULL, UNIQUE                 | UUID-based key for filesystem lookup      |
| `uploadedAt` | `TIMESTAMP` | NOT NULL, immutable              | Set by `@PrePersist`; never updated       |
| `user_id`    | `BIGINT`    | FK → `users(id)`, CASCADE DELETE | Owner of the image                        |

> **Composite unique constraint:** `(user_id, name, format)` : prevents a user from uploading the same file in the same format twice.

#### `processingJob`

| Column             | Type             | Constraints                      | Notes                                                       |
| ------------------ | ---------------- | -------------------------------- | ----------------------------------------------------------- |
| `id`               | `BIGINT`         | PK, AUTO                         | Identity-generated                                          |
| `type`             | `VARCHAR` (enum) | NOT NULL                         | `FORMAT_CONVERSION` \| `BACKGROUND_REMOVAL`                 |
| `status`           | `VARCHAR` (enum) | NOT NULL                         | `PENDING` \| `RUNNING` \| `DONE` \| `FAILED`                |
| `outputName`       | `VARCHAR`        | NOT NULL                         | User-supplied name for the output file                      |
| `targetFormat`     | `VARCHAR`        | NOT NULL                         | Desired output format, stored lowercase                     |
| `targetStorageKey` | `VARCHAR`        | NULLABLE                         | Populated after execution; used to retrieve the result file |
| `image_id`         | `BIGINT`         | FK → `image(id)`, CASCADE DELETE | Source image for this job                                   |

> **Composite unique constraint:** `(image_id, outputName)` : prevents duplicate output names per image.

### 3.2 Relationships

```
users  1 --> N  image  1 --> N  processingJob
```

One `User` owns zero or more `Image` records. One `Image` is the source for zero or more `ProcessingJob` records. Both foreign keys carry `CASCADE DELETE` semantics : deleting a user removes all their images and, transitively, all associated jobs.

---

## 4. Processing Pipeline

### 4.1 Job Lifecycle State Machine

Every `ProcessingJob` follows a deterministic state machine from creation to completion.

| From      | Trigger                             | To        | Consequence                                              |
| --------- | ----------------------------------- | --------- | -------------------------------------------------------- |
| _(none)_  | `POST /api/images/{id}/jobs`        | `PENDING` | Job row inserted; no I/O started                         |
| `PENDING` | `POST /api/jobs/{id}/process`       | `RUNNING` | Async execution started; `targetStorageKey` assigned     |
| `RUNNING` | `ImageProcessor.execute()` succeeds | `DONE`    | `targetStorageKey` persisted; output file accessible     |
| `RUNNING` | Any exception during execution      | `FAILED`  | Exception absorbed; status recorded; no result available |

### 4.2 Supported Job Types

| `JobType`            | Implementation                        | Notes                                                                                                       |
| -------------------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `FORMAT_CONVERSION`  | FFmpeg via `ProcessBuilder`           | Converts between image formats. FFmpeg exit code ≠ 0 throws `IOException` → job set to `FAILED`.            |
| `BACKGROUND_REMOVAL` | File-copy stub (10 s simulated delay) | AI integration placeholder. Real model call to be wired in Sprint 2 without changing the service interface. |

### 4.3 Asynchronous Execution

The processing controller immediately returns `HTTP 202 Accepted` after creating the job record and launching execution asynchronously via `@Async` (backed by `AsyncConfig`). The client polls `GET /api/jobs/{id}` to observe status transitions.

This design is forward-looking: the `@Async` boundary here becomes a message-queue boundary in Sprint 2 with minimal changes to the orchestration logic in `ProcessingJobService`.

---

## 5. REST API Reference

All endpoints are under the `/api` prefix. Requests and responses use JSON. File uploads use `multipart/form-data`. All error responses follow the uniform structure described in [[#5.4 Error Response Structure|Section 5.4]].

### 5.1 User Endpoints

| Method   | Path                     | Status         | Description                                                               |
| -------- | ------------------------ | -------------- | ------------------------------------------------------------------------- |
| `POST`   | `/api/users`             | 201 Created    | Register a new user. Body: `{ username, email }`. Returns `UserResponse`. |
| `GET`    | `/api/users`             | 200 OK         | List all registered users. Returns `UserResponse[]`.                      |
| `GET`    | `/api/users/{id}/images` | 200 OK         | List all images owned by a user. Returns `ImageResponse[]`.               |
| `DELETE` | `/api/users/{id}`        | 204 No Content | Delete user and cascade-delete all their images and jobs.                 |

### 5.2 Image Endpoints

| Method   | Path                      | Status         | Description                                                                                                                                 |
| -------- | ------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST`   | `/api/images?userId={id}` | 201 Created    | Upload an image file (`multipart/form-data`, field: `file`). Validates magic bytes. Returns `Location` header pointing to the new resource. |
| `GET`    | `/api/images/{id}`        | 200 OK         | Retrieve image metadata. Returns `ImageResponse`.                                                                                           |
| `DELETE` | `/api/images/{id}`        | 204 No Content | Delete image and all associated jobs from DB and storage.                                                                                   |
| `POST`   | `/api/images/{id}/jobs`   | 201 Created    | Create a processing job for an image. Body: `{ type, outputName, targetFormat }`. Returns `JobResponse`.                                    |
| `GET`    | `/api/images/{id}/jobs`   | 200 OK         | List all jobs for a given image. Returns `JobResponse[]`.                                                                                   |

### 5.3 Job Endpoints

|Method|Path|Status|Description|
|---|---|---|---|
|`POST`|`/api/jobs/{id}/process`|202 Accepted|Trigger async execution of a `PENDING` job. Returns `JobResponse` with current status.|
|`GET`|`/api/jobs/{id}`|200 OK|Poll job status. Returns `JobResponse`.|
|`GET`|`/api/jobs/{id}/result`|200 OK|Download the output file if status is `DONE`. Returns binary stream with `Content-Disposition`.|
|`DELETE`|`/api/jobs/{id}`|204 No Content|Delete a job. If status is `DONE`, the output file is also removed from storage.|

### 5.4 Error Response Structure

All error responses share a uniform JSON body:

```json
{
  "timestamp": "2026-06-06T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Image not found with ID: 42",
  "path": "/api/images/42",
  "validationErrors": null
}
```

### 5.5 HTTP Status Code Summary

|Status|Condition|
|---|---|
|200 OK|Successful read or poll|
|201 Created|Resource created (user, image, job)|
|202 Accepted|Job execution launched asynchronously|
|204 No Content|Successful delete|
|400 Bad Request|Invalid payload, missing fields, path variable constraint violation|
|404 Not Found|Resource does not exist, or job result not yet available|
|409 Conflict|Duplicate record (same username/email, or same output name for an image)|
|413 Content Too Large|Upload exceeds configured maximum file size|
|415 Unsupported Media Type|File magic bytes do not match a supported image format|
|500 Internal Server Error|Unexpected runtime error (disk I/O failure, FFmpeg crash, etc.)|

---

## 6. Cross-Cutting Design Decisions

### 6.1 Storage Model

Files are stored in a flat directory configured via the `storage.data-dir` property (default: `/tmp/imageprocessing/data`). Both uploaded originals and processed outputs share the same directory, distinguished by a UUID-based `storageKey` rather than a path hierarchy.

`StorageService` validates that every resolved path starts with the configured base directory before performing any I/O, preventing path-traversal vulnerabilities. The `storageKey` is persisted in `Image.storageKey` (for uploads) and `ProcessingJob.targetStorageKey` (for outputs). The filename presented to the client on download is taken from `ProcessingJob.outputName`, keeping internal storage keys opaque.

### 6.2 File Format Validation

File format is validated by reading the first bytes of the upload stream (magic-byte detection in `ImageFormatValidator`), not by trusting the file extension or the `Content-Type` header. This prevents trivial bypasses where a non-image file is renamed with an image extension. Unsupported formats produce an `HTTP 415` response via `UnsupportedFileFormatException`, handled centrally in `GlobalExceptionHandler`.

### 6.3 Transaction Strategy

Service methods that modify state use `@Transactional` with `READ_COMMITTED` isolation for writes involving multiple entities, and `readOnly = true` for pure reads. The async job execution runs intentionally outside any long-lived transaction: the `targetStorageKey` assignment and final status update are committed in a short transaction, while the actual FFmpeg I/O happens outside any transaction context to avoid holding database connections during potentially long-running operations.

### 6.4 DTO Layer

Controllers never expose JPA entities directly. All responses are mapped to immutable Java records (`ImageResponse`, `JobResponse`, `UserResponse`, `ErrorResponse`). This prevents accidental serialisation of lazy-loaded associations (`LazyInitializationException`) and decouples the API schema from the database schema.

### 6.5 Observability

Critical service and storage methods are annotated with `@Observed` (Micrometer), producing traces and metrics compatible with Prometheus/Grafana or any OpenTelemetry-compatible backend. This establishes an observability baseline that carries forward into the containerised microservice deployment in Sprint 2.

---

## 7. Sprint 1 Completion Status

| Acceptance Criterion                                     | Status | Evidence                                                     |
| -------------------------------------------------------- | ------ | ------------------------------------------------------------ |
| Dashboard has file input and submit button               | Done   | `index.html` / `script.js`                                   |
| `POST /api/images` saves file and creates `Image` record | Done   | `ImageController`, `ImageService.handleImageUpload`          |
| `ProcessingJob` created with status `PENDING` on upload  | Done   | `ImageService.createJob`                                     |
| Job status `PENDING → RUNNING → DONE/FAILED`             | Done   | `ProcessingJobService.startAsyncProcessExecution`            |
| Output file written to disk                              | Done   | `ImageProcessor.execute` → `StorageService.storeLocalFile`   |
| Output file path persisted in job (`targetStorageKey`)   | Done   | `ImageProcessor.execute` returns and sets `targetStorageKey` |
| Download link for `DONE` jobs in dashboard               | Done   | `script.js` `downloadResult()`, `GET /api/jobs/{id}/result`  |
| `HTTP 404` if job not `DONE` or has no result            | Done   | `ProcessingJobController.downloadJobResult` guard            |
| `HTTP 404` if job ID does not exist                      | Done   | `GlobalExceptionHandler` → `ResourceNotFoundException`       |
| Architecture documented                                  | Done   | This document (Task 9)                                       |
