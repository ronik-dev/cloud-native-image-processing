# Implement file upload REST endpoint
>This guide is specific to Arch Linux. It assumes database migrations and core entity relationships are active.

##### Context
To optimize resource usage and prevent client timeouts during high-intensity image processing operations, data ingestion is structurally decoupled from task execution.
- Asset Ingestion (ImageUploadController): Validates multipart file payloads, saves binary contents to a filesystem path configured dynamically in the application properties, and persists tracking metadata with an initial PENDING job status.
- Pipeline Execution (ProcessingJobController): Separates heavy computational workloads by exposing a dedicated endpoint to trigger processing execution on-demand.

Project Structure Changes
```
src/main/java/ch/supsi/imageprocessing/
└── controller/
    ├── ImageUploadController.java   
    └── ProcessingJobController.java  
```
    
### Steps
##### 1. Implement File Upload Controller and Request Mapping
Create the custom `ImageUploadController` to handle standard multipart form data (`multipart/form-data`).

- `@RestController`: Marks the class as a web controller where every method automatically serializes the return value directly into the HTTP response body.
- `@RequestMapping("/api")`: Establishes a base URI routing path prefix for all endpoints contained within the class.
- `@PostMapping("/upload")`: Configures an endpoint to intercept incoming POST requests for file ingestion.
- `@RequestParam("file") MultipartFile`: Injects and binds the uploaded binary stream payload from the multipart request body.
- `@RequestParam(value = "userId", defaultValue = "1") Long`: Captures a query or form parameter to pass the associated owner identification directly to the underlying business logic.
- `@Value("${storage.upload-dir:...}")`: Dynamically injects the target upload folder path string declared directly inside the centralized application.properties configuration file.

The controller validates that the incoming file payload is present and contains a valid file extension. It creates any missing parent directories on the host filesystem and writes the binary stream to disk. It delegates to `ImageService.submitUpload` to handle metadata persistence and initialize the tracking job within a single transaction. Finally, it uses `ServletUriComponentsBuilder` to construct and return a HATEOAS-compliant Location header pointing directly back to the newly created image resource path.

2. Implement the Processing Job Lifecycle Controller
Create the custom ProcessingJobController to explicitly isolate job execution from file web operations:
- `@RestController`: Registers the class as a web endpoint layer component.
- `@RequestMapping("/api")`: Maps the standard internal routing context prefix.
- `@PostMapping("/jobs/{id}/process")`: Intercepts explicit execution commands targeting a specific job resource.
- `@PathVariable Long id`: Extracts the unique job entry identifier directly from the incoming URL path.

The controller receives the request and delegates the workflow execution to the processing engine layer (ImageService.processJob), moving the entity lifecycle states from PENDING to RUNNING, and finally setting it to DONE (or FAILED) before returning an HTTP 200 OK response with the updated job payload.

3. Run and Verify Lifecycle Steps via cURL
Boot your application context locally:

``` Bash
mvn spring-boot:run
Ensure a target user exists in your data layer to satisfy relational constraints before running tests.
```

###### Step A: Ingest the raw image
Submit a multipart form-data request containing a local file binary to the upload endpoint.
- Expected Result: An `HTTP 201` Created status code response accompanied by a `Location` header mapping back to the standard persistence engine path (e.g., `http://localhost:8080/images/1`). A file is written to your configured scratch directory, and a PENDING tracking record is inserted into the job database table.

###### Step B: Execute the processing pipeline manually
Submit an empty POST request directly to the process endpoint using the job identifier created during ingestion.
- Expected Result: An `HTTP 200 OK` status response containing a structured JSON response body showing that the job status field has updated from `PENDING` to `DONE`, along with a valid absolute absolute path pointing to the processed output asset on disk.
