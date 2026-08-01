package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Bridges Keycloak identity to the domain User model (Sprint 1 schema).
 * These are deliberately separate concepts -- Keycloak owns *who can log
 * in*, this table owns *who owns which images/jobs* -- so this endpoint is
 * the one place that translates one into the other, via find-or-create
 * keyed on the authenticated principal's username (UserService#findOrCreateUser).
 */
@RestController
public class MeController {

		private final OrchestratorClient orchestratorClient;

		public MeController(OrchestratorClient orchestratorClient) {
				this.orchestratorClient = orchestratorClient;
		}

		@GetMapping("/api/me")
		public UserResponse me(@AuthenticationPrincipal OidcUser principal) {
				// preferred_username/email come from the "profile"/"email" scopes
				// already requested in application.properties
				// (spring.security.oauth2.client.registration.keycloak.scope).
				String username = principal.getPreferredUsername();
				String email = principal.getEmail();
				return orchestratorClient.findOrCreateUser(username, email);
		}

		@GetMapping("/api/me/images")
		public List<ImageResponse> getMyImages(@AuthenticationPrincipal OidcUser principal) {
				String username = principal.getPreferredUsername();
				String email = principal.getEmail();
				UserResponse user = orchestratorClient.findOrCreateUser(username, email);
				return orchestratorClient.getUserImages(user.id());
		}

		@DeleteMapping("/api/me")
		public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal OidcUser principal) {
				// 1. Identify the user from the token
				String username = principal.getPreferredUsername();
				String email = principal.getEmail();

				// 2. Find their local ID
				UserResponse user = orchestratorClient.findOrCreateUser(username, email);

				// 3. Trigger the cascade deletion in the orchestrator
				orchestratorClient.deleteUser(user.id());

				return ResponseEntity.noContent().build();
		}
}
