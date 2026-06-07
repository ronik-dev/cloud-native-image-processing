#### 1. Endpoint Evolution and UI Query Filtering
* **Before:** Exposed a generic collection endpoint (`GET /api/images`) that dumped all metadata rows into a single flat array regardless of ownership.
* **After:** Replaced with a user-centric contextual path mapping (`GET /api/users/{userId}/images`). This filters imagery streams at the business validation layer before return, providing the UI with snappy, profile-specific asset loads without parsing overhead on the client side.

#### 2. Isolation of the Execution Pipeline
* **Before:** The workflow responsible for creating a job (`createConversionJob`) was tightly coupled with asset transformation. It executed file writing operations synchronously within the core metadata transaction, blocking database worker threads until physical processing completed.
* **After:** The synchronous processing heavy-lifting has been completely extracted. `ImageService` now exclusively records intent and manages foundational tracking parameters. A dedicated infrastructure coordinator, `ProcessingJobService`, explicitly handles background processing triggers, tracking lifecycle state changes smoothly (`PENDING` → `RUNNING` → `DONE` or `FAILED`) without blocking metadata reads.

#### 3. Data Transfer Object (DTO) Representation
* **Before:** Relational models were transmitted across the wire directly, forcing Jackson to evaluate proxy entities and triggering `LazyInitializationException` boundary leaks outside active persistence contexts.
* **After:** The application implements flat immutable data contracts via Java Records (`ImageResponse` and `JobResponse`). Bidirectional structural dependencies are condensed into simple primitives (e.g., nesting a full `User` object graph is reduced to a single primitive `userId` field). This isolates the HTTP serialization engine from open database sessions.

#### 4. Physical Storage Separation
* **Before:** Web controllers handled file writing mechanics, input stream manipulation, and host storage path composition directly inside request parsing loops.
* **After:** File I/O routines are decoupled into an independent `StorageService` layer using the Java NIO Filesystem API. This abstracts parent directory setup and input sanitation. By isolating local host paths inside this singular module, the app gains a clean path to swap local disk workflows for cloud-native object storage buckets (e.g., AWS S3 or MinIO) down the line without changing api endpoint contracts.

---

### Testing Strategy & Verification Updates

To ensure these changes operate cleanly without breaking runtime environments, the validation infrastructure was split into distinct operational test slices:

* **Web Slice Testing (`@WebMvcTest`):** Specialized controller slices test specific HTTP request mapping verbs, multi-part form streaming, response statuses, location headers, and validation errors while completely mocking out underlying database connection frameworks via Mockito boundaries.
* **Service Context Isolation:** Testing boundaries for `ImageService` were streamlined to omit file transformation parameters. Concurrently, a dedicated validation suite was implemented for `ProcessingJobService` to independently verify asynchronous pipeline execution states and runtime error recovery.
* **Sandbox Filesystem Testing (`@TempDir`):** Local storage operations are tested
