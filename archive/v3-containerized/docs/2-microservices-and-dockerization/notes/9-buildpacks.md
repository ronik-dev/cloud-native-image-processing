# BuildPacks: Alternative Image Build Strategy
> This document details the Cloud Native Buildpacks integration explored as an alternative to the hand-authored Dockerfiles for the Java microservices. This approach is strictly experimental and is not carried forward into the thesis.

---

### 1. What are BuildPacks?

Cloud Native Buildpacks (CNB) are a CNCF standard that automatically detect an application's language runtime, resolve its dependencies, and produce an OCI-compliant container image — entirely without a `Dockerfile`. The Spring Boot Maven Plugin ships with the **Paketo Buildpacks** provider out of the box, making this a zero-configuration option for the Java services in this project.

No additional plugins or dependencies are required. Since `spring-boot-maven-plugin` is already declared in both `gateway/pom.xml` and `orchestrator/pom.xml`, BuildPacks support is immediately available.

---

### 2. POM Configuration

To configure the output image name directly in the POM rather than passing it on the command line at every build, add an `<image>` block to the existing plugin declaration in each module:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <image>
            <name>imageprocessing/${project.artifactId}:buildpack</name>
        </image>
    </configuration>
</plugin>
```

---

### 3. Execution

Images are built by invoking the `build-image` Maven goal. A running Docker daemon is required on the host machine. The Paketo builder image is pulled automatically on the first run.

```bash
# Build the Gateway image
mvn -pl gateway spring-boot:build-image -DskipTests

# Build the Orchestrator image
mvn -pl orchestrator spring-boot:build-image -DskipTests
```

> **Note:** The Python AI Worker is explicitly excluded from this approach. The Paketo Java buildpack only covers JVM projects, and the Worker's `glibc`/PyTorch dependency constraints and custom `uv` + `.venv` entrypoint make the hand-authored Dockerfile the only viable build path.

---

### 4. Trade-offs vs. Hand-Authored Dockerfiles

| Concern | Manual Dockerfile | BuildPacks |
|---|---|---|
| **Image size** | Minimal — only JRE + JAR (`alpine/java:21-jre`) | Larger — Paketo base layers included |
| **Security hardening** | Explicit `appuser` non-root enforcement | Non-root by default (`cnb` user), but not customizable |
| **Transparency** | Full control over every layer | Opaque — build logic lives inside the buildpack |
| **Build speed (cold)** | Fast — Maven layer cache reused | Slower — builder image pulled on first run |
| **Build speed (warm)** | Fast — Docker layer cache on POM changes | Fast — CNB lifecycle caches the dependency layer |
| **CI/CD fit** | Requires Docker CLI and `docker build` | Native `mvn` integration, no Docker CLI required |
| **Python Worker** | Fully supported | Not applicable — JVM-only |

BuildPacks reduce boilerplate and apply security defaults automatically, making them well-suited to environments where Docker CLI access is restricted. For this architecture, however, the explicit privilege hardening, minimal Alpine-based image sizes, and Python Worker constraints make the manual Dockerfiles the superior and canonical build path.

