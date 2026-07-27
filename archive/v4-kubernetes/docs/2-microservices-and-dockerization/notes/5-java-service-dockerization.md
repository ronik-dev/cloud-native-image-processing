# Containerize Java Services: Orchestrator and Gateway

> This guide is OS specific for Arch Linux, as this project is developed on this OS. 
> These instructions assume you have `docker` installed and the Docker daemon running (`sudo systemctl start docker`).
> All configuration edits assume the use of a standard text editor (like Neovim) in the terminal.

### Prerequisites: Docker and Buildx

> This guide is OS-specific for Arch Linux, as this project is developed on this OS. 
> These instructions assume you have an internet connection and `pacman` configured.

##### Docker Engine
Docker is required to build and run the isolated container environments for the microservices.

1. Install the Docker package:
    ```Bash
    sudo pacman -S docker
    ```
2. Enable and start the Docker daemon so it runs automatically on boot:
    ```Bash
    sudo systemctl enable --now docker
    ```
3. Add your user to the `docker` group so you can execute Docker commands without elevating privileges via `sudo`. *Note: You must log out and log back in (or restart your terminal session) for this group change to take effect.*
    ```Bash
    sudo usermod -aG docker $USER
    ```
4. Verify the installation:
    ```Bash
    docker --version
    ```
    Output should look like:
    ```Bash
    Docker version 27.x.x, build ...
    ```

##### Docker Buildx
Buildx is a Docker CLI plugin that extends the `docker build` command with the full support of Moby BuildKit. It is essential for executing the multi-stage builds and utilizing the layer caching mechanisms defined in our Dockerfiles.

1. Install the Buildx plugin:
    ```Bash
    sudo pacman -S docker-buildx
    ```
2. Verify the installation:
    ```Bash
    docker buildx version
    ```
    Output should look like:
    ```Bash
    [github.com/docker/buildx](https://github.com/docker/buildx) v0.x.x ...
    ```

### 1. Cloud-Native Configuration Updates
Before creating the container images, the Spring Boot configuration must be decoupled from the local environment. Hardcoded database hosts and forced profiles prevent the container from resolving external network services or accepting environment variables.

1. Open `orchestrator/src/main/resources/application.properties` and `gateway/src/main/resources/application.properties`.
2. Apply the following parameterizations:
   * **Remove forced profiles:** Comment out or delete `spring.profiles.active=local`.
   * **Parameterize the DB host:** Replace `localhost` with `${POSTGRES_HOST:localhost}`.
   * **Add safe fallbacks:** Ensure URLs have default values (e.g., `${WORKER_URL:http://localhost:8082}`).

Example of the updated `orchestrator` properties:
```properties
spring.application.name=orchestrator
spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/${POSTGRES_DB_NAME:cloud_native_db}
spring.datasource.username=${POSTGRES_USER_NAME}
spring.datasource.password=${POSTGRES_USER_PASSWORD}
spring.jpa.hibernate.ddl-auto=create

logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
management.tracing.sampling.probability=1.0
logging.file.name=logs/image-processor.log

server.servlet.context-path=/internal
server.port=8081

worker.url=${WORKER_URL:http://localhost:8082}
```

### 2. Multi-Stage Dockerfiles
We use a two-stage build process. The `build` stage compiles the code using the Maven Alpine image, caching dependencies to speed up rebuilds. The runtime stage uses a minimal JRE Alpine image and drops `root` privileges for security.

##### Orchestrator Dockerfile
1. Create `orchestrator/Dockerfile`:
    ```dockerfile
    # Stage 1: Build
    FROM maven:3.9-eclipse-temurin-21-alpine AS build
    WORKDIR /build

    # Copy POMs and resolve dependencies first to utilize Docker layer caching
    COPY pom.xml .
    COPY common/pom.xml common/
    COPY orchestrator/pom.xml orchestrator/
    COPY gateway/pom.xml gateway/
    RUN mvn -B dependency:go-offline

    # Copy source and build
    COPY common/src common/src
    COPY orchestrator/src orchestrator/src
    # -am (also make) automatically builds the required 'common' dependency
    RUN mvn -B clean package -DskipTests -pl orchestrator -am

    # Stage 2: Runtime
    FROM eclipse-temurin:21-jre-alpine
    WORKDIR /app

    # Create a non-root user for security compliance (Kubernetes Pod Security Standards)
    RUN addgroup -S appgroup && adduser -S appuser -G appgroup

    COPY --from=build /build/orchestrator/target/orchestrator-*.jar app.jar
    RUN chown appuser:appgroup app.jar
    USER appuser

    EXPOSE 8081
    ENTRYPOINT ["java", "-jar", "app.jar"]
    ```

##### Gateway Dockerfile
1. Create `gateway/Dockerfile`. The structure is identical to the orchestrator, with adjustments to the target module and exposed port:
    ```dockerfile
    # Stage 1: Build
    FROM maven:3.9-eclipse-temurin-21-alpine AS build
    WORKDIR /build

    COPY pom.xml .
    COPY common/pom.xml common/
    COPY orchestrator/pom.xml orchestrator/
    COPY gateway/pom.xml gateway/
    RUN mvn -B dependency:go-offline

    COPY common/src common/src
    COPY gateway/src gateway/src
    RUN mvn -B clean package -DskipTests -pl gateway -am

    # Stage 2: Runtime
    FROM eclipse-temurin:21-jre-alpine
    WORKDIR /app

    RUN addgroup -S appgroup && adduser -S appuser -G appgroup
    COPY --from=build /build/gateway/target/gateway-*.jar app.jar
    RUN chown appuser:appgroup app.jar
    USER appuser

    EXPOSE 8080
    ENTRYPOINT ["java", "-jar", "app.jar"]
    ```

### 3. Build and Test
The builds must be executed from the root of the repository so the Docker context has access to the `common` module.

1. Build the images:
    ```Bash
    docker buildx build -f orchestrator/Dockerfile -t imageprocessing/orchestrator:dev .
    docker buildx build -f gateway/Dockerfile -t imageprocessing/gateway:dev .
    ```

2. Verify the images:
    ```Bash
    docker images
    ```
    Output should show the heavily optimized runtime sizes:
    ```Bash
    IMAGE                              ID             DISK USAGE   CONTENT SIZE   EXTRA
    imageprocessing/gateway:dev        <..........>        349MB          108MB    
    imageprocessing/orchestrator:dev   <..........>        433MB          148MB    
    ```

3. Test execution locally (bypassing the container network to test against local bare-metal PostgreSQL):
    ```Bash
    docker run --network host \
      -e POSTGRES_USER_NAME=my_app_user \
      -e POSTGRES_USER_PASSWORD=my_secure_password \
      imageprocessing/orchestrator:dev
    ```
    Look for the successful startup log:
    ```Bash
    Started CloudNativeImageProcessingApplication in X.XXX seconds
    ```
    Press `Ctrl+C` to stop the container.
