# 14 Logging and Distributed Tracing (task: 21)
This guide is OS specific for Arch Linux, as this project is developed on this os
It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
These guide assumes you have an internet connection pacman and yay working and installed.

### Context
To support the transition toward a cloud-native microservices architecture, standard console logging is insufficient for tracking requests that span multiple boundaries or background threads. The application integrates the Micrometer Observation API and the OpenTelemetry SDK to automatically generate W3C-compliant Trace IDs and Span IDs. Rather than exporting these metrics to an external visualization engine (which will be implemented in a future phase of the project), the system intercepts tracing lifecycle events and streams them directly into the standard application logs. These logs, enriched with their distributed trace context, are output to the console and simultaneously persisted to a local file on the host machine.

### Tools and Dependencies
- **Spring Boot Starter Actuator**: Provides the core `Micrometer Observation API` to automatically intercept incoming HTTP requests and measure their execution boundaries.
- **Spring Boot Starter OpenTelemetry**: Supplies the core OpenTelemetry engine required to generate the unique Trace and Span IDs, managing the active context in memory without requiring manual tracer instantiation.

### Setup and Changes Made
##### 1. Maven Configuration Updates
The Actuator and OpenTelemetry starters were explicitly declared in the project build file. Legacy trace-specific dependencies, bridge modules, and BOMs were removed to allow the Spring Boot parent POM to safely enforce library version compatibility.

##### 2. Application Properties Configuration
The central configuration file was updated to inject the generated tracing context directly into the application's logging output. The logging pattern level was modified to dynamically pull the Trace ID and Span ID from the Mapped Diagnostic Context `MDC`. The trace sampling probability was forced to 100% to capture every single request for local development. Finally, physical file logging was enabled to capture and write all output to logs/image-processor.log.

##### 3. Observation Text Publisher Setup
A dedicated configuration component was implemented to enable Aspect-Oriented Programming `AOP` detection of internal telemetry. An ObservationTextPublisher bean was registered to intercept the start, open, and stop lifecycle events of the trace spans. Instead of packaging these for network transport, it forwards them directly to the standard SLF4J logger, providing immediate, text-based execution timelines in the terminal.

##### 4. Asynchronous Context Propagation
Because asynchronous execution explicitly drops the incoming HTTP thread context, the Spring async task executor was reconfigured. A custom thread pool was created and equipped with a ContextPropagatingTaskDecorator. This ensures that any background worker threads pulled for the image processing pipeline seamlessly inherit the original Trace ID of the HTTP request that triggered the operation.

##### 5. Internal Service Instrumentation
Core business logic that falls outside of the framework's automatic network boundaries was explicitly instrumented using @Observed annotations. Specifically, the disk I/O routines inside the storage service layer—handling local file writes, multipart extractions, and deletions—were tagged. This allows heavy physical storage operations to appear as distinct, cleanly measured blocks within the tracing stream.
