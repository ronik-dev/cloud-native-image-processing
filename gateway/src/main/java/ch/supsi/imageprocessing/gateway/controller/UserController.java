package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.UserRequest;
import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.validation.annotation.Validated;   


import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

		private final OrchestratorClient orchestratorClient;

		public UserController(OrchestratorClient orchestratorClient) {
				this.orchestratorClient = orchestratorClient;
		}

		@PostMapping
		public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
				UserResponse response = orchestratorClient.createUser(request);
				return ResponseEntity.status(201).body(response);
		}

		@GetMapping
		public ResponseEntity<List<UserResponse>> getUsers() {
				return ResponseEntity.ok(orchestratorClient.getUsers());
		}

		@GetMapping("/{id}/images")
		public ResponseEntity<List<ImageResponse>> getUserImages(@PathVariable @Min(0) Long id) {
				return ResponseEntity.ok(orchestratorClient.getUserImages(id));
		}

		@DeleteMapping("/{id}")
		public ResponseEntity<Void> deleteUser(@PathVariable @Min(0) Long id) {
				orchestratorClient.deleteUser(id);
				return ResponseEntity.noContent().build();
		}
}
