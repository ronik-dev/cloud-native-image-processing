package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.JobRequest;
import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final OrchestratorClient orchestratorClient;

    public ImageController(OrchestratorClient orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageResponse> getImage(@PathVariable Long id) {
        return ResponseEntity.ok(orchestratorClient.getImage(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        orchestratorClient.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/jobs")
    public ResponseEntity<JobResponse> createJob(
            @PathVariable Long id,
            @Valid @RequestBody JobRequest request) {
        return ResponseEntity.status(201).body(orchestratorClient.createJob(id, request));
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<JobResponse>> getImageJobs(@PathVariable Long id) {
        return ResponseEntity.ok(orchestratorClient.getImageJobs(id));
    }
}
