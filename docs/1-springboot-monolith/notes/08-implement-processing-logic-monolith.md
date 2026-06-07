# 8 Implement Processing Logic (Monolith)
> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes the Spring Boot project is fully initialized, PostgreSQL is running, core entities are configured, and both ImageService and UserService are implemented.

### Context

This task introduces the synchronous processing execution pipeline for the monolithic stage of the application. To prepare for an eventual transition into a decoupled, containerized cloud-native architecture without introducing premature networking or distributed systems complexity, the implementation isolates concerns into two distinct components:

1. **`ImageService` (State & Orchestration):** Manages database boundaries, tracks entity records, changes state transitions (`PENDING` -> `RUNNING` -> `DONE`/`FAILED`), and guards data transactions.
2. **`ImageProcessor` (Physical Asset Processing):** A dedicated Spring `@Component` that abstracts the CPU-intensive local OS file operations, keeping the core transactional service layer insulated from filesystem manipulation side effects.

Furthermore, path handling is architectural: instead of leaking internal storage locations to the outside web tier, the orchestrator registers business intent (`outputName`, `targetFormat`), and the internal worker determines the exact physical destination path dynamically.

---

### Project structure changes
```
src/main/java/ch/supsi/imageprocessing/
├── processor/
│   └── ImageProcessor.java
└── service/
    └── ImageService.java

src/test/java/ch/supsi/imageprocessing/
└── ImageServiceTests.java
```

---

### Steps

##### 1. Implement the Isolated Worker Component
Create the file `src/main/java/ch/supsi/imageprocessing/processor/ImageProcessor.java` to isolate file transformations using Java's NIO filesystem API

##### 2. Wire the Synchronous Processing Pipeline
Update `src/main/java/ch/supsi/imageprocessing/service/ImageService.java` to integrate the processor using clean field autowiring/injection, forcing database synchronization updates through saveAndFlush() to guarantee lifecycle `transparency:Javapackage` `ch.supsi.imageprocessing.service`;

##### 3. Processing State Machine Specification
|Current Status|Trigger Action|Target Status|Relational Consequence|
|--|--|--|--|
|PENDING|createConversionJob|RUNNING|Persists initial job row to track pipeline entry.|
|RUNNING|imageProcessor.execute success|DONE|Binds generated absolute metadata string to resultPath.|
|RUNNING|Checked IOException raised|FAILED|Gracefully absorbs process failure to prevent runtime transaction leakage.|

##### 4. Write Isolated Lifecycle Tests
Ensure `src/test/java/ch/supsi/imageprocessing/ImageServiceTests.java` captures structural interactions. Mock checked exceptions via Mockito and intercept save parameters using dynamic thenAnswer reflection injections to handle database-generated primary keys within an isolated runtime container environment.

##### 5. Run the verification test suite
Verify compilation signatures and run all tests locally using your terminal:

``` Bash
mvn test
```
Expected output verification layout:

```
[INFO] Running ch.supsi.imageprocessing.service.UserServiceTest
[INFO] Tests run: n, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
