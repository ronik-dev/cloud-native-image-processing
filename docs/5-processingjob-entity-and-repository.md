# 5 Processing Job Entity and Repository
> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes the Spring Boot project is already initialized, PostgreSQL is running, and both User and Image entities are implemented.

### Context

This task introduces the processing pipeline domain entity of the application: `ProcessingJob`.
In JPA, an entity is a Java class mapped to a database table. Hibernate reads the annotations at startup and creates the corresponding table automatically, because `spring.jpa.hibernate.ddl-auto=update` is set in `application.properties`.

The `ProcessingJobRepository` interface extends `JpaRepository` and gives access to CRUD operations without writing any implementation — Spring Data JPA generates it at runtime.

---

### Project structure changes
```
src/main/java/ch/supsi/imageprocessing/
├── entity/
│   ├── ProcessingJob.java
│   ├── JobType.java
│   └── JobStatus.java
└── repository/
    └── ProcessingJobRepository.java

src/test/java/ch/supsi/imageprocessing/
└── ProcessingJobTests.java
```

---

### Steps

##### 1. Create the Enums and ProcessingJob class
1. Create the file src/main/java/ch/supsi/imageprocessing/entity/JobType.java and populate it with the possible job types.
2. Create the file src/main/java/ch/supsi/imageprocessing/entity/JobStatus.java and populate it with the possible job statuses.

| Annotation | Effect |
|---|---|
| `@Entity` | Maps the class to a database table |
| `@Table(name = "processingJob")` | Sets the table name explicitly|
| `@Id` | Marks the primary key |
| `@GeneratedValue(IDENTITY)` | Delegates id generation to PostgreSQL (auto-increment) |
| `@Column(nullable = false, unique = true)` | Adds NOT NULL and UNIQUE constraints to the column |
| `@ManyToOne` | Declares that multiple images can belong to a single user resource |
| `@JoinColumn(name = "user_id", nullable = false)` | Generates the foreign key relationship constraint targeting the users table |
| `@Enumerated(EnumType.STRING)` | Safely persists enum names as readable text (VARCHAR) rather than volatile positional integers (INT) | 

> Note on setId(): No setter is provided for id. Hibernate sets the field directly via reflection (Field.setAccessible(true)) and does not need a setter. Exposing a public setId() would allow application code to corrupt the primary key.

##### 2. Create the repository
Create the file `src/main/java/ch/supsi/imageprocessing/repository/ImageRepository.java`:
```java
package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
}
```
No implementation is needed. Spring Data JPA generates it at startup.
`JpaRepository<ProcessingJob, Long>` means: entity type is `ProcessingJob`, primary key type is `Long`.

This automatically exposes the following methods:
```java
userRepository.save(processingJob)
userRepository.findById(id)
userRepository.findAll()
userRepository.deleteById(id)
userRepository.count()
```

Custom queries can be added by declaring method names following Spring's naming convention.

##### 3. Add test dependencies
Should be already added, see guide user guide (3-user-entity-and-repository.md)

##### 4. Write the repository tests
Create the file `src/test/java/ch/supsi/imageprocessing/ProcessingJobTests.java`:
> Created Spring Boot integration tests for ProcessingJobRepository to verify job persistence, retrieval, and database-level unique output name constraints.

##### 5. Run the tests
```bash
mvn test
```
Expected output:
```
[INFO] Tests run: n, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

##### 6. Verify with HAL Explorer
Start the application and open the HAL Explorer:
```bash
mvn spring-boot:run
```
Navigate to `http://localhost:8080/explorer`.

The `/images` endpoint should appear in the list of available resources.
You can POST a new user directly from the explorer to confirm end-to-end persistence:
>You **MUST** have already created and saved an user, in this example the user links.self.href is "http://localhost:8080/users/1"
```json
{
  "type": "FORMAT_CONVERSION",
  "status": "PENDING",
  "outputName": "output_processed_image",
  "targetFormat": "png",
  "image": "http://localhost:8080/api/images/1"
}
```
