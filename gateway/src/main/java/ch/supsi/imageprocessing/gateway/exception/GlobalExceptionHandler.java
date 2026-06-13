package ch.supsi.imageprocessing.gateway.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ch.supsi.imageprocessing.common.dto.ErrorResponse;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(
            WebClientResponseException ex) {
        ErrorResponse error = new ErrorResponse(
            LocalDateTime.now(),
            ex.getStatusCode().value(),
            ex.getStatusCode().toString(),
            ex.getMessage(),
            null,
            null
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
