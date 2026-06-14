package ch.supsi.imageprocessing.gateway.controller;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.JobRequest;
import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.gateway.client.OrchestratorClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.MediaType;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@Valid
public class ImageController {

		private final OrchestratorClient orchestratorClient;

		public ImageController(OrchestratorClient orchestratorClient) {
				this.orchestratorClient = orchestratorClient;
		}

		@GetMapping("/{id}")
		public ResponseEntity<ImageResponse> getImage(@PathVariable @Min(0) Long id) {
				return ResponseEntity.ok(orchestratorClient.getImage(id));
		}

		@DeleteMapping("/{id}")
		public ResponseEntity<Void> deleteImage(@PathVariable @Min(0) Long id) {
				orchestratorClient.deleteImage(id);
				return ResponseEntity.noContent().build();
		}

		@PostMapping("/{id}/jobs")
		public ResponseEntity<JobResponse> createJob(
						@PathVariable @Min(0) Long id,
						@Valid @RequestBody JobRequest request) {
				return ResponseEntity.status(201).body(orchestratorClient.createJob(id, request));
		}

		@GetMapping("/{id}/jobs")
		public ResponseEntity<List<JobResponse>> getImageJobs(@PathVariable @Min(0) Long id) {
				return ResponseEntity.ok(orchestratorClient.getImageJobs(id));
		}

		@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
		public ResponseEntity<ImageResponse> uploadImage(
						@RequestParam("file") MultipartFile file,
						@RequestParam("userId") Long userId) {
				ImageResponse response = orchestratorClient.uploadImage(file, userId);
				return ResponseEntity.status(201).body(response);
		}
}
