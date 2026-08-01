package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import ch.supsi.imageprocessing.gateway.config.JacksonConfig;
import ch.supsi.imageprocessing.gateway.config.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.verify;


@WebMvcTest(MeController.class)
@ActiveProfiles("test")
@Import({JacksonConfig.class, SecurityConfig.class})
class MeControllerUnitTest {

		@Autowired
		private MockMvc mockMvc;

		@MockitoBean
		private OrchestratorClient orchestratorClient;

		@Test
		void shouldReturnCurrentUser() throws Exception {
				// Arrange
				UserResponse mockResponse = new UserResponse(1L, "testuser", "test@test.ch");
				Mockito.when(orchestratorClient.findOrCreateUser("testuser", "test@test.ch")).thenReturn(mockResponse);

				// Act & Assert
				mockMvc.perform(get("/api/me")
								// Simulate an authenticated user with an OIDC token containing specific claims
								.with(oidcLogin().idToken(token -> token
												.claim("preferred_username", "testuser")
												.claim("email", "test@test.ch")))
								.accept(MediaType.APPLICATION_JSON))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.id").value(1))
						.andExpect(jsonPath("$.username").value("testuser"))
						.andExpect(jsonPath("$.email").value("test@test.ch"));
		}
		@Test
		void shouldDeleteMe() throws Exception {
				// Arrange
				UserResponse mockResponse = new UserResponse(1L, "testuser", "test@test.ch");
				Mockito.when(orchestratorClient.findOrCreateUser("testuser", "test@test.ch")).thenReturn(mockResponse);
				Mockito.doNothing().when(orchestratorClient).deleteUser(1L);

				// Act & Assert
				mockMvc.perform(delete("/api/me")
								.with(oidcLogin().idToken(token -> token
												.claim("preferred_username", "testuser")
												.claim("email", "test@test.ch"))))
						.andExpect(status().isNoContent());

				verify(orchestratorClient).deleteUser(1L);
		}
}
