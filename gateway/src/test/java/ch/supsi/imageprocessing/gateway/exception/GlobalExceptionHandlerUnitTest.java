package ch.supsi.imageprocessing.gateway.exception;

import ch.supsi.imageprocessing.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerUnitTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper to test the actual deserialization logic[cite: 1]
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); 
        
        exceptionHandler = new GlobalExceptionHandler(objectMapper);
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void shouldHandleWebClientExceptionWithValidErrorResponse() {
        // 1. Arrange: Create a fake JSON ErrorResponse coming from the Orchestrator
        String validJsonError = """
                {
                  "timestamp": "2026-07-29T10:00:00",
                  "status": 404,
                  "error": "Not Found",
                  "message": "User not found",
                  "path": "/internal/users/99",
                  "validationErrors": null
                }
                """;

        WebClientResponseException ex = WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, validJsonError.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );

        // 2. Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleWebClientException(ex, request);

        // 3. Assert: The handler should parse the JSON and pass the exact message through[cite: 1]
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User not found", response.getBody().message());
        assertEquals(404, response.getBody().status());
        // Verify it overrides the path with the gateway's request URI[cite: 1]
        assertEquals("/api/test", response.getBody().path()); 
    }

    @Test
    void shouldHandleWebClientExceptionWithFallbackForInvalidJson() {
        // 1. Arrange: Create an exception with a non-JSON body (e.g., plain text error from a proxy)
        String invalidJsonError = "Gateway Timeout";

        WebClientResponseException ex = WebClientResponseException.create(
                504, "Gateway Timeout", HttpHeaders.EMPTY, invalidJsonError.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );

        // 2. Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleWebClientException(ex, request);

        // 3. Assert: The handler should catch the parsing error and use the fallback response[cite: 1]
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(504, response.getBody().status());
        assertEquals("Upstream service error", response.getBody().message());
        assertEquals("/api/test", response.getBody().path());
    }

    @Test
    void shouldHandleUncheckedIOException() {
        // 1. Arrange
        UncheckedIOException ex = new UncheckedIOException("Read failed", new IOException());

        // 2. Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUncheckedIO(ex, request);

        // 3. Assert: Verify it returns 500 and the specific file upload message[cite: 1]
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("Failed to process uploaded file", response.getBody().message());
        assertEquals("/api/test", response.getBody().path());
    }

    @Test
    void shouldHandleGeneralException() {
        // 1. Arrange
        IllegalArgumentException ex = new IllegalArgumentException("Something went wrong");

        // 2. Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneral(ex, request);

        // 3. Assert: Verify the generic 500 fallback[cite: 1]
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertEquals("/api/test", response.getBody().path());
    }
}
