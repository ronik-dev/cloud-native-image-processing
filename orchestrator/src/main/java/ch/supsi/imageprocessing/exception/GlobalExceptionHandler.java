package ch.supsi.imageprocessing.exception;

import ch.supsi.imageprocessing.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpMediaTypeNotSupportedException;   
import org.springframework.dao.DataIntegrityViolationException;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

		// Handle Custom Business Exceptions
		@ExceptionHandler(ResourceNotFoundException.class)
		public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.NOT_FOUND, 
								"Not Found", 
								ex.getMessage(), 
								request.getRequestURI(), 
								null);
		}

		// Handle Custom Business Exceptions
		@ExceptionHandler(InvalidRequestException.class)
		public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.BAD_REQUEST, 
								"Bad Request", 
								ex.getMessage(), 
								request.getRequestURI(), 
								null);
		}

		// Handles wrong file format exception (Custom and httpmedia...)
		@ExceptionHandler({UnsupportedFileFormatException.class, HttpMediaTypeNotSupportedException.class})
		public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
								"Unsupported Media Type", 
								ex.getMessage(), 
								request.getRequestURI(), 
								null
								);
		}

		// Handle Java Standard Exceptions 
		@ExceptionHandler(IllegalArgumentException.class)
		public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.BAD_REQUEST, 
								"Bad Request", 
								ex.getMessage(), 
								request.getRequestURI(), 
								null);
		}

		// Handle @Valid Validation Errors (from Request Body)
		@ExceptionHandler(MethodArgumentNotValidException.class)
		public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
				Map<String, String> errors = new HashMap<>();
				ex.getBindingResult().getFieldErrors().forEach(error -> 
								errors.put(error.getField(), error.getDefaultMessage()));
				return buildResponse(
								HttpStatus.BAD_REQUEST, 
								"Validation Error", 
								"Invalid request payload", 
								request.getRequestURI(), 
								errors);
		}

		// Handle @Validated Constraints (from Path Variables like @Min(0))
		@ExceptionHandler(ConstraintViolationException.class)
		public ResponseEntity<ErrorResponse> handleConstraintViolations(ConstraintViolationException ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.BAD_REQUEST, 
								"Validation Error", 
								ex.getMessage(), 
								request.getRequestURI(), 
								null);
		}

		// Catch-All for Unexpected Errors (e.g., Disk I/O runtime exceptions in StorageService)
		@ExceptionHandler(Exception.class)
		public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
				// TODO: Log the actual exception trace here using SLF4J (e.g., log.error("Unhandled exception", ex);)
				return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", request.getRequestURI(), null);
		}


		// Handle MultipartException subclass thrown when an upload exceeds the maximum upload size allowed.
		@ExceptionHandler(MaxUploadSizeExceededException.class)
		public ResponseEntity<ErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.CONTENT_TOO_LARGE, 
								"Payload Too Large", 
								"The uploaded file exceeds the maximum allowed size.", 
								request.getRequestURI(), 
								null);
		}		

		// Handle Data integrity violations errors coming from the DB
		@ExceptionHandler(DataIntegrityViolationException.class)
		public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
				return buildResponse(
								HttpStatus.CONFLICT, 
								"Conflict", 
								"A record with this information already exists (e.g., duplicate username or email).", 
								request.getRequestURI(), 
								null
								);
		}


		private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message, String path, Map<String, String> validationErrors) {
				ErrorResponse response = new ErrorResponse(LocalDateTime.now(), status.value(), error, message, path, validationErrors);
				return new ResponseEntity<>(response, status);
		}
}
