package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;   
import org.springframework.validation.annotation.Validated;   
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/jobs")
@Validated
public class JobController {

		private final OrchestratorClient orchestratorClient;

		public JobController(OrchestratorClient orchestratorClient) {
				this.orchestratorClient = orchestratorClient;
		}

		@PostMapping("/{id}/process")
		public ResponseEntity<JobResponse> processJob(@PathVariable @Min(0) Long id) {
				return ResponseEntity.accepted().body(orchestratorClient.processJob(id));
		}

		@GetMapping("/{id}")
		public ResponseEntity<JobResponse> getJob(@PathVariable @Min(0) Long id) {
				return ResponseEntity.ok(orchestratorClient.getJob(id));
		}

		@DeleteMapping("/{id}")
		public ResponseEntity<Void> deleteJob(@PathVariable @Min(0) Long id) {
				orchestratorClient.deleteJob(id);
				return ResponseEntity.noContent().build();
		}

		@GetMapping("/{id}/result")
		public ResponseEntity<byte[]> downloadJobResult(@PathVariable @Min(0) Long id) {
				ResponseEntity<byte[]> orchestratorResponse = orchestratorClient.downloadJobResult(id);

				return ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION,orchestratorResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
						.contentType(MediaType.APPLICATION_OCTET_STREAM)
						.body(orchestratorResponse.getBody());
		}
}
