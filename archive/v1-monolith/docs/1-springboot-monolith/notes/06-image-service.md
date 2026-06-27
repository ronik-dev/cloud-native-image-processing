# 6 Image Service Layer
> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes the Spring Boot project is already initialized, PostgreSQL is running, and core domain entities are configured.

### Context

This task introduces a dedicated service layer (`ImageService`) to encapsulate business domain validation and transaction boundaries, decoupling database manipulation rules from the REST endpoints.

By defining transaction attributes and custom exceptions (`InvalidRequestException`, `ResourceNotFoundException`), the architecture ensures input errors fail-fast before hitting database threads and structural state drops are transactional.

---

### Project structure changes
```
src/main/java/ch/supsi/imageprocessing/
├── exception/
│   ├── InvalidRequestException.java
│   └── ResourceNotFoundException.java
└── service/
    └── ImageService.java

src/test/java/ch/supsi/imageprocessing/
└── ImageServiceTest.java
```
---

### Steps

##### 1. Create the Custom Exceptions
1. Create `src/main/java/ch/supsi/imageprocessing/exception/InvalidRequestException.java` to capture semantic validation and bad user inputs (maps to HTTP 400).
2. Create `src/main/java/ch/supsi/imageprocessing/exception/ResourceNotFoundException.java` to handle missing query entities (maps to HTTP 404).

##### 2. Build the Service Layer
Create `src/main/java/ch/supsi/imageprocessing/service/ImageService.java` utilizing constructor injection to handle transaction workflows

##### 3. Write Isolated Unit Tests
Create unit tests utilizing Mockito framework extensions inside src/test/java/ch/supsi/imageprocessing/ImageServiceTest.java. Ensure failure behaviors check against the new custom domain exception signatures.

##### 4. Run the tests
Verify execution parameters via terminal:

```Bash
mvn test
```
Expected output:
```
[INFO] Running ch.supsi.imageprocessing.ImageServiceTest
[INFO] Tests run: n, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

