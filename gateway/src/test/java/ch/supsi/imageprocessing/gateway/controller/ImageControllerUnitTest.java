package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.JobRequest;
import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import ch.supsi.imageprocessing.gateway.config.JacksonConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; 
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet;

import java.time.LocalDateTime;   

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfig.class) 
class ImageControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrchestratorClient orchestratorClient;

    @Test
    void shouldUploadImageSuccessfully() throws Exception {
        // Arrange
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test-image.png", MediaType.IMAGE_PNG_VALUE, "dummy image content".getBytes()
        );
		ImageResponse mockResponse = new ImageResponse(1L, "test-image.png", "png", LocalDateTime.now(), 5L);
        
        Mockito.when(orchestratorClient.uploadImage(any(), eq(5L))).thenReturn(mockResponse);

        // Act & Assert: Uses multipart() builder to test @PostMapping with MULTIPART_FORM_DATA_VALUE[cite: 1]
        mockMvc.perform(multipart("/api/images")
                .file(mockFile)
                .param("userId", "5")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("test-image.png"));
    }

    @Test
    void shouldCreateJobSuccessfully() throws Exception {
		JobResponse mockResponse = new JobResponse(10L, "FORMAT_CONVERSION", "PENDING", "output_img", 1L);
        Mockito.when(orchestratorClient.createJob(eq(1L), any(JobRequest.class))).thenReturn(mockResponse);

        String requestBody = "{\"type\": \"FORMAT_CONVERSION\", \"outputName\": \"output_img\", \"targetFormat\": \"jpg\"}";

        // Act & Assert: Expect 201 Created status[cite: 1]
        mockMvc.perform(post("/api/images/1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldDeleteImage() throws Exception {
        // Arrange
        Mockito.doNothing().when(orchestratorClient).deleteImage(1L);

        // Act & Assert: Expect 204 No Content status[cite: 1]
        mockMvc.perform(delete("/api/images/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(orchestratorClient).deleteImage(1L);
    }

    @Test
    void shouldFailValidationWhenImageIdIsNegative() throws Exception {
        // Act & Assert: The @Min(0) constraint[cite: 1] should catch negative IDs
        mockMvc.perform(delete("/api/images/-5"))
                .andExpect(status().isBadRequest());
    }
}
