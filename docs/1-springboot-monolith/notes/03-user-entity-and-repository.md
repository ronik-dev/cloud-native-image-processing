# 3 User Entity and Repository
> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes the Spring Boot project is already initialized and PostgreSQL is running (see guides 1 and 2).

### Context
This task introduces the first domain entity of the application: `User`.
In JPA, an entity is a Java class mapped to a database table. Hibernate reads the annotations at startup and creates the corresponding table automatically, because `spring.jpa.hibernate.ddl-auto=update` is set in `application.properties`.

The `UserRepository` interface extends `JpaRepository` and gives access to CRUD operations without writing any implementation — Spring Data JPA generates it at runtime.

---

### Project structure changes
```
src/main/java/ch/supsi/imageprocessing/
├── entity/
│   └── User.java
└── repository/
    └── UserRepository.java

src/test/java/ch/supsi/imageprocessing/
└── UserRepositoryTests.java
```

---

### Steps

##### 1. Create the entity package and User class
Create the file `src/main/java/ch/supsi/imageprocessing/entity/User.java`:
>Created the User JPA entity mapped to the users table with auto-generated IDs and unique username and email fields.

Key annotations:
| Annotation | Effect |
|---|---|
| `@Entity` | Maps the class to a database table |
| `@Table(name = "users")` | Sets the table name explicitly — avoids conflict with the reserved SQL keyword `user` |
| `@Id` | Marks the primary key |
| `@GeneratedValue(IDENTITY)` | Delegates id generation to PostgreSQL (auto-increment) |
| `@Column(nullable = false, unique = true)` | Adds NOT NULL and UNIQUE constraints to the column |

> **Note on `setId()`:** No setter is provided for `id`. Hibernate sets the field directly via
> reflection (`Field.setAccessible(true)`) and does not need a setter. Exposing a public
> `setId()` would allow application code to corrupt the primary key.

##### 2. Create the repository
Create the file `src/main/java/ch/supsi/imageprocessing/repository/UserRepository.java`:
```java
package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
```
No implementation is needed. Spring Data JPA generates it at startup.
`JpaRepository<User, Long>` means: entity type is `User`, primary key type is `Long`.

This automatically exposes the following methods:
```java
userRepository.save(user)
userRepository.findById(id)
userRepository.findAll()
userRepository.deleteById(id)
userRepository.count()
```

Custom queries can be added by declaring method names following Spring's naming convention:
```java
Optional<User> findByUsername(String username);
Optional<User> findByEmail(String email);
```

##### 3. Add test dependencies
Add the following to the `<dependencies>` block in `pom.xml`:
```xml
<!-- Spring Boot test support — JUnit 5, AssertJ, Mockito -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- H2 in-memory database — used by tests only, never in production -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

> **Why H2 for tests?** The production database is PostgreSQL. Tests use H2 (an in-memory
> database) so they run without requiring a running PostgreSQL instance and without polluting
> production data. Both are JPA-compatible, so the same entity and repository code works
> for both. Testcontainers (running real PostgreSQL in Docker for tests) is introduced in Sprint 4.

##### 4. Write the repository tests
Create the file `src/test/java/ch/supsi/imageprocessing/UserRepositoryTests.java`:
> Created Spring Boot integration tests for UserRepository to verify user persistence, retrieval, and database-level unique username constraints.

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

The `/users` endpoint should appear in the list of available resources.
You can POST a new user directly from the explorer to confirm end-to-end persistence:
```json
{
  "username": "nicola",
  "email": "nicola@supsi.ch"
}
```
