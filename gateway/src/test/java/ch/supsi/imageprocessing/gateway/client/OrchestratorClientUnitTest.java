package ch.supsi.imageprocessing.gateway.client;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.UserRequest;
import ch.supsi.imageprocessing.common.dto.UserResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;

class OrchestratorClientUnitTest {

    private MockWebServer mockWebServer;
    private OrchestratorClient orchestratorClient;

    @BeforeEach
    void setUp() throws IOException {
        // Start the mock server
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Configure a real WebClient pointing to the mock server's dynamically assigned URL
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        // Instantiate the client with the WebClient[cite: 1]
        orchestratorClient = new OrchestratorClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldCreateUserAndDeserializeResponse() throws Exception {
        // 1. Arrange: Queue a mock JSON response from the server
        String mockJsonResponse = "{\"id\": 1, \"username\": \"testuser\", \"email\": \"test@test.ch\"}";
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        UserRequest requestDto = new UserRequest("testuser", "test@test.ch");

        // 2. Act: Call the client method[cite: 1]
        UserResponse response = orchestratorClient.createUser(requestDto);

        // 3. Assert: Verify the response was mapped correctly back to the DTO
        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("testuser", response.username());

        // Verify the client sent the request to the correct URI and with the right HTTP method[cite: 1]
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/users", recordedRequest.getPath());
    }

    @Test
    void shouldDeleteUserSuccessfully() throws Exception {
        // 1. Arrange: Queue an empty 204 No Content response
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        // 2. Act: Call the void method[cite: 1]
        assertDoesNotThrow(() -> orchestratorClient.deleteUser(99L));

        // 3. Assert: Verify the correct URI was called[cite: 1]
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("DELETE", recordedRequest.getMethod());
        assertEquals("/users/99", recordedRequest.getPath());
    }

    @Test
    void shouldUploadImageSuccessfully() throws Exception {
        // 1. Arrange: Queue the mock response
        String mockJsonResponse = "{\"id\": 10, \"filename\": \"test.png\", \"format\": \"png\"}";
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.png", MediaType.IMAGE_PNG_VALUE, "dummy content".getBytes()
        );

        // 2. Act: Call uploadImage[cite: 1]
        ImageResponse response = orchestratorClient.uploadImage(mockFile, 5L);

        // 3. Assert: Verify response
        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("test.png", response.filename());

        // Verify the request details
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/images", recordedRequest.getPath());
        assertTrue(recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE).contains("multipart/form-data"));
    }

    @Test
    void shouldThrowUncheckedIOExceptionWhenFileUploadFails() {
        // 1. Arrange: Create a faulty MultipartFile that throws an IOException on getBytes()
        MockMultipartFile faultyFile = new MockMultipartFile("file", "test.png", MediaType.IMAGE_PNG_VALUE, new byte[0]) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("Simulated disk error");
            }
        };

        // 2. Act & Assert: Verify that the try-catch block in OrchestratorClient wraps the error[cite: 1]
        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> {
            orchestratorClient.uploadImage(faultyFile, 5L);
        });

        assertEquals("Failed to read upload file", exception.getMessage());
    }

    @Test
    void shouldDownloadJobResultSuccessfully() throws Exception {
        // 1. Arrange: Queue a binary response
        byte[] fakeImageBytes = "fake-binary-data".getBytes();
        mockWebServer.enqueue(new MockResponse()
                .setBody(new okio.Buffer().write(fakeImageBytes))
                .addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"result.png\"")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE));

        // 2. Act: Call downloadJobResult[cite: 1]
        var responseEntity = orchestratorClient.downloadJobResult(42L);

        // 3. Assert
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertArrayEquals(fakeImageBytes, responseEntity.getBody());
        assertEquals("attachment; filename=\"result.png\"", responseEntity.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertEquals("/jobs/42/result", recordedRequest.getPath());
    }
}
