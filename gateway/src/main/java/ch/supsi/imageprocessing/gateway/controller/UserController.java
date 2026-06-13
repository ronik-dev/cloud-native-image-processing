package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.UserRequest;
import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
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
    public ResponseEntity<List<ImageResponse>> getUserImages(@PathVariable Long id) {
        return ResponseEntity.ok(orchestratorClient.getUserImages(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        orchestratorClient.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
