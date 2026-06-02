package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.JobResponse;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.service.StorageService;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api")
public class ProcessJobController{

		@Autowired
		private ImageService imageService;

		@Autowired
		private StorageService storageService;

		@PostMapping("/jobs/{id}/process")
		public ResponseEntity<JobResponse> triggerProcessing(@PathVariable Long id) {
				ProcessingJob updatedJob = imageService.processJob(id);
				return ResponseEntity.ok(JobResponse.fromEntity(updatedJob));
		}

		@GetMapping("/jobs/{id}")
		public ResponseEntity<JobResponse> getJobStatus(@PathVariable Long id) {
				ProcessingJob job = imageService.getJobStatus(id);
				return ResponseEntity.ok(JobResponse.fromEntity(job));
		}

		@GetMapping("/jobs/{id}/result")
		public ResponseEntity<Resource> downloadJobResult(@PathVariable Long id) {
				ProcessingJob job = imageService.getJobStatus(id);

				if (job.getStatus() != JobStatus.DONE || job.getResultPath() == null || job.getResultPath().isBlank()) {
						throw new ResourceNotFoundException("Processed file output is not available for Job ID: " + id);
				}

				Resource fileResource = storageService.getResource(job.getResultPath());

				return ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getOutputName() + "\"")
						.contentType(MediaType.APPLICATION_OCTET_STREAM) // Safely fallback to binary streaming
						.body(fileResource);
		}
}
