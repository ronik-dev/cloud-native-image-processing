package ch.supsi.imageprocessing.gateway.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ch.supsi.imageprocessing.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.UncheckedIOException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(
            WebClientResponseException ex, HttpServletRequest request) {
        
        try {
            ErrorResponse orchestratorError = ex.getResponseBodyAs(ErrorResponse.class);
            if (orchestratorError != null) {
                ErrorResponse gatewayError = new ErrorResponse(
                    orchestratorError.timestamp(),
                    orchestratorError.status(),
                    orchestratorError.error(),
                    orchestratorError.message(),
                    request.getRequestURI(),
                    orchestratorError.validationErrors()
                );
                return ResponseEntity.status(ex.getStatusCode()).body(gatewayError);
            }
        } catch (Exception ignored) {
            // orchestrator did not return an ErrorResponse body, fall through
        }

        ErrorResponse fallback = new ErrorResponse(
            LocalDateTime.now(),
            ex.getStatusCode().value(),
            ex.getStatusCode().toString(),
            "Upstream service error",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(ex.getStatusCode()).body(fallback);
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ErrorResponse> handleUncheckedIO(
            UncheckedIOException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            LocalDateTime.now(),
            500,
            "Internal Server Error",
            "Failed to process uploaded file",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(500).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            LocalDateTime.now(),
            500,
            "Internal Server Error",
            "An unexpected error occurred",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(500).body(error);
    }
}
