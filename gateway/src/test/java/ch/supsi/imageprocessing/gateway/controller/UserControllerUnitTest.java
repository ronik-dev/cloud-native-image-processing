package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.UserRequest;
import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import ch.supsi.imageprocessing.gateway.config.JacksonConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; 
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.List;
import java.time.LocalDateTime;   

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfig.class)
class UserControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrchestratorClient orchestratorClient;

    @Test
    void shouldCreateUser() throws Exception {
        // Arrange
        UserResponse mockResponse = new UserResponse(1L, "testuser", "test@test.ch");
        Mockito.when(orchestratorClient.createUser(any(UserRequest.class))).thenReturn(mockResponse);

        String requestBody = "{\"username\": \"testuser\", \"email\": \"test@test.ch\"}";

        // Act & Assert: Expect 201 Created status[cite: 1]
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void shouldGetUserImages() throws Exception {
        // Arrange
		List<ImageResponse> mockImages = List.of(new ImageResponse(100L, "image.png", "png", LocalDateTime.now(), 1L));
        Mockito.when(orchestratorClient.getUserImages(1L)).thenReturn(mockImages);

        // Act & Assert
        mockMvc.perform(get("/api/users/1/images")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    void shouldFailValidationWhenUserIdIsNegative() throws Exception {
        // Act & Assert: The @Min(0) constraint on the ID path variable should trigger a 400 Bad Request[cite: 1]
        mockMvc.perform(get("/api/users/-1/images")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteUser() throws Exception {
        // Arrange
        Mockito.doNothing().when(orchestratorClient).deleteUser(1L);

        // Act & Assert: Expect 204 No Content status[cite: 1]
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
        
        Mockito.verify(orchestratorClient).deleteUser(1L);
    }
}
