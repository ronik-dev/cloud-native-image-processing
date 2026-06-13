package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final OrchestratorClient orchestratorClient;

    public JobController(OrchestratorClient orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<JobResponse> processJob(@PathVariable Long id) {
        return ResponseEntity.accepted().body(orchestratorClient.processJob(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(orchestratorClient.getJob(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        orchestratorClient.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
}
