# 4 Image Entity and Repository
> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes the Spring Boot project is already initialized and PostgreSQL is running (see guides 1 and 2).

### Context
This task introduces the first domain entity of the application: `Image`.
In JPA, an entity is a Java class mapped to a database table. Hibernate reads the annotations at startup and creates the corresponding table automatically, because `spring.jpa.hibernate.ddl-auto=update` is set in `application.properties`.

The `ImageRepository` interface extends `JpaRepository` and gives access to CRUD operations without writing any implementation — Spring Data JPA generates it at runtime.

---

### Project structure changes
```
src/main/java/ch/supsi/imageprocessing/
├── entity/
│   └── Image.java
└── repository/
    └── ImageRepository.java

src/test/java/ch/supsi/imageprocessing/
└── ImageRepositoryTests.java
```

---

### Steps

##### 1. Create the entity package and Image class
Create the file `src/main/java/ch/supsi/imageprocessing/entity/Image.java`:
>Created the Image JPA entity mapped to the image table with auto-generated IDs, binary storage mapping, and a many-to-one link to a User.

Key annotations:
| Annotation | Effect |
|---|---|
| `@Entity` | Maps the class to a database table |
| `@Table(name = "image")` | Sets the table name explicitly|
| `@Id` | Marks the primary key |
| `@GeneratedValue(IDENTITY)` | Delegates id generation to PostgreSQL (auto-increment) |
| `@Column(nullable = false, unique = true)` | Adds NOT NULL and UNIQUE constraints to the column |
| `@Column(columnDefinition = "BYTEA")` | Explicitly maps the raw byte[] to a PostgreSQL binary storage field | 
| `@ManyToOne` | Declares that multiple images can belong to a single user resource |
| `@JoinColumn(name = "user_id", nullable = false)` | Generates the foreign key relationship constraint targeting the users table |

> Note on setId(): No setter is provided for id. Hibernate sets the field directly via reflection (Field.setAccessible(true)) and does not need a setter. Exposing a public setId() would allow application code to corrupt the primary key.

##### 2. Create the repository
Create the file `src/main/java/ch/supsi/imageprocessing/repository/ImageRepository.java`:
```java
package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
}
```
No implementation is needed. Spring Data JPA generates it at startup.
`JpaRepository<Image, Long>` means: entity type is `Image`, primary key type is `Long`.

This automatically exposes the following methods:
```java
userRepository.save(image)
userRepository.findById(id)
userRepository.findAll()
userRepository.deleteById(id)
userRepository.count()
```

Custom queries can be added by declaring method names following Spring's naming convention:
```java
Optional<Image> findByName(String name);
```

##### 3. Add test dependencies
Should be already added, see guide user guide (3-user-entity-and-repository.md)

##### 4. Write the repository tests
Create the file `src/test/java/ch/supsi/imageprocessing/ImageRepositoryTests.java`:
> Created Spring Boot integration tests for ImageRepository to verify user persistence, retrieval, and database-level unique username constraints.

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
  "name": "vacation_photo",
  "format": "png",
  "image": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
  "user": "http://localhost:8080/api/users/1"
}
```
