# 7 User Service Layer (task 17)
> This guide is OS specific for Arch Linux, as this project is developed on this OS.
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> This guide assumes the Spring Boot project is already initialized, PostgreSQL is running, and the User entity is fully implemented.

### Context

This task introduces a dedicated service layer (`UserService`) to encapsulate core account lifecycle operations. By establishing explicit business domain validation and clean transactional boundaries, the database manipulation constraints remain strictly isolated from the REST controller layer.

Leveraging specific Spring optimization features—such as `@Transactional(readOnly = true)`—ensures fetch workflows bypass Hibernate dirty-checking overhead for increased performance stability, while standard queries reject empty parameters before opening database connections.

---

### Project structure changes
```
src/main/java/ch/supsi/imageprocessing/
└── service/
    └── UserService.java

src/test/java/ch/supsi/imageprocessing/
└── service/
    └── UserServiceTest.java
```
---

### Steps

##### 1. Create the UserService class
Create the file `src/main/java/ch/supsi/imageprocessing/service/UserService.java` using constructor injection to map database lookups:

##### 2. Key Transaction Configuration Properties
|Annotation / Strategy	|Operational Benefit|
|--|--|
|@Service|	Automatically registers the class implementation inside Spring's IoC context as a thread-safe default singleton bean.|
|@Transactional|	Marks the execution boundary for write tasks, ensuring user profile updates execute atomically.|
|@Transactional(readOnly = true)|	Disables automated Hibernate state dirty-checking snapshots, optimizing read-heavy profile lookup speeds.|

##### 3. Write Isolated Unit Tests
Create unit tests using the Mockito framework tracking engine inside src/test/java/ch/supsi/imageprocessing/service/UserServiceTest.java. Ensure failure workflows expect your domain boundary exceptions (InvalidRequestException and ResourceNotFoundException).

##### 4. Run the verification test suite
Verify compilation signatures and run all tests locally using your terminal:

``` Bash
mvn test
```
Expected output verification layout:

```
[INFO] Running ch.supsi.imageprocessing.service.UserServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
