package ch.supsi.imageprocessing.gateway.exception;

import ch.supsi.imageprocessing.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.UncheckedIOException;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

		private final ObjectMapper objectMapper;

		public GlobalExceptionHandler(ObjectMapper objectMapper) {
				this.objectMapper = objectMapper;
		}

		@ExceptionHandler(WebClientResponseException.class)
		public ResponseEntity<ErrorResponse> handleWebClientException(
						WebClientResponseException ex, HttpServletRequest request) {

				System.out.println("WebClientResponseException caught");
				System.out.println("Status: " + ex.getStatusCode());
				System.out.println("Body: " + ex.getResponseBodyAsString());
				ex.printStackTrace();
				String rawBody = ex.getResponseBodyAsString();

				if (rawBody != null && !rawBody.isBlank()) {
						try {
								ErrorResponse orchestratorError = objectMapper.readValue(
												rawBody, ErrorResponse.class
												);
								return ResponseEntity.status(ex.getStatusCode()).body(
												new ErrorResponse(
														orchestratorError.timestamp(),
														orchestratorError.status(),
														orchestratorError.error(),
														orchestratorError.message(),
														request.getRequestURI(),
														orchestratorError.validationErrors()
														)
												);
						} catch (Exception ignored) {
								// body was not a valid ErrorResponse, fall through to fallback
						}
				}

				return ResponseEntity.status(ex.getStatusCode()).body(
								new ErrorResponse(
										LocalDateTime.now().toString(),
										ex.getStatusCode().value(),
										ex.getStatusCode().toString(),
										"Upstream service error",
										request.getRequestURI(),
										null
										)
								);
		}

		@ExceptionHandler(UncheckedIOException.class)
		public ResponseEntity<ErrorResponse> handleUncheckedIO(
						UncheckedIOException ex, HttpServletRequest request) {
				return ResponseEntity.status(500).body(new ErrorResponse(
										LocalDateTime.now().toString(),
										500,
										"Internal Server Error",
										"Failed to process uploaded file",
										request.getRequestURI(),
										null
										));
		}

		@ExceptionHandler(Exception.class)
		public ResponseEntity<ErrorResponse> handleGeneral(
						Exception ex, HttpServletRequest request) {

				System.out.println("General exception caught: " + ex.getClass().getName());
				System.out.println("Message: " + ex.getMessage());
				ex.printStackTrace();
				return ResponseEntity.status(500).body(new ErrorResponse(
										LocalDateTime.now().toString(),
										500,
										"Internal Server Error",
										"An unexpected error occurred",
										request.getRequestURI(),
										null
										));
		}
}
