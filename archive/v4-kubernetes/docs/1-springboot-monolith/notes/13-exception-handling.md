# 13 Exception Handling (task 21)
> This guide is OS specific for Arch Linux, as this project is developed on this os
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> These guide assumes you have an internet connection pacman and yay working and installed.

### Context
To provide a reliable and predictable integration for frontend applications, the backend must never leak internal stack traces or default Tomcat HTML error pages. All exceptions thrown during the image ingestion and processing lifecycle are structurally intercepted and translated into a standardized, machine-readable JSON format. This approach isolates the client from internal database or filesystem implementation details while clearly differentiating between client faults `4xx` and server faults `5xx`.

### Setup and Changes Made
##### 1. Error Payload Standardization
A centralized Data Transfer Object `ErrorResponse` was introduced to standardize all error responses. This ensures the frontend UI expects the exact same JSON schema regardless of which endpoint failed. The structure includes a timestamp, the HTTP status code, a short error phrase, a specific message, the URI path where the error occurred, and an optional map for detailed validation errors.

##### 2. Domain-Specific Exceptions
The application's business logic throws custom runtime exceptions to represent specific failure states. Classes like ResourceNotFoundException, InvalidRequestException, and UnsupportedFileFormatException act as clean domain signals without being tightly coupled to the web layer.

##### 3. Global Interception Layer
A global exception handler `GlobalExceptionHandler` was implemented using Spring's @RestControllerAdvice. This component catches both custom domain exceptions and framework-level errors `such as MaxUploadSizeExceededException, MethodArgumentNotValidException, and DataIntegrityViolationException` before they reach the servlet boundary. It maps these exceptions to their appropriate HTTP status codes and builds the unified ErrorResponse payload, seamlessly formatting constraints and validation failures into the response map
