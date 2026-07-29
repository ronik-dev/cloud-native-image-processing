package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import ch.supsi.imageprocessing.gateway.config.JacksonConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; 
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@Import(JacksonConfig.class)
class JobControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrchestratorClient orchestratorClient;

    @Test
    void shouldProcessJobSuccessfully() throws Exception {
        // Arrange
		JobResponse mockResponse = new JobResponse(1L, "FORMAT_CONVERSION", "RUNNING", "hero_banner", 1L);        Mockito.when(orchestratorClient.processJob(1L)).thenReturn(mockResponse);

        // Act & Assert: Expect 202 Accepted status for processing[cite: 1]
        mockMvc.perform(post("/api/jobs/1/process")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void shouldGetJobSuccessfully() throws Exception {
        // Arrange
		JobResponse mockResponse = new JobResponse(1L, "FORMAT_CONVERSION", "DONE", "hero_banner", 1L);        
		Mockito.when(orchestratorClient.getJob(1L)).thenReturn(mockResponse);

        // Act & Assert: Expect 200 OK status[cite: 1]
        mockMvc.perform(get("/api/jobs/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void shouldDownloadJobResultSuccessfully() throws Exception {
        // Arrange
        byte[] mockFileContent = "dummy image data".getBytes();
        String contentDisposition = "attachment; filename=\"hero_banner.png\"";
        
        // Mock the ResponseEntity returned by the OrchestratorClient[cite: 1]
        ResponseEntity<byte[]> mockResponseEntity = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(mockFileContent);
                
        Mockito.when(orchestratorClient.downloadJobResult(1L)).thenReturn(mockResponseEntity);

        // Act & Assert: Verify headers, content type, and byte body[cite: 1]
        mockMvc.perform(get("/api/jobs/1/result"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, contentDisposition))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(mockFileContent));
    }

    @Test
    void shouldDeleteJobSuccessfully() throws Exception {
        // Arrange
        Mockito.doNothing().when(orchestratorClient).deleteJob(1L);

        // Act & Assert: Expect 204 No Content status[cite: 1]
        mockMvc.perform(delete("/api/jobs/1"))
                .andExpect(status().isNoContent());

        Mockito.verify(orchestratorClient).deleteJob(1L);
    }

    @Test
    void shouldFailValidationWhenJobIdIsNegative() throws Exception {
        // Act & Assert: The @Min(0) constraint should catch negative IDs[cite: 1]
        mockMvc.perform(get("/api/jobs/-1"))
                .andExpect(status().isBadRequest());
    }
}
