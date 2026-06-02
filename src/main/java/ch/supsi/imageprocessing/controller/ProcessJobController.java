package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.JobResponse;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ProcessingJobService;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.service.StorageService;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProcessJobController{


		@Autowired
		private ProcessingJobService pjs;

		@Autowired
		private StorageService ss;


		@GetMapping("/jobs")
		public ResponseEntity<List<JobResponse>> getAllJobs() {
				List<JobResponse> jobs = pjs.getAllJobs()
						.stream()
						.map(JobResponse::fromEntity)
						.toList();
;
				return ResponseEntity.ok(jobs);
		}

		@PostMapping("/jobs/{id}/process")
		public ResponseEntity<JobResponse> triggerProcessing(@PathVariable Long id) {
				ProcessingJob updatedJob = pjs.processJob(id);
				return ResponseEntity.ok(JobResponse.fromEntity(updatedJob));
		}

		@GetMapping("/jobs/{id}")
		public ResponseEntity<JobResponse> getJobStatus(@PathVariable Long id) {
				ProcessingJob job = pjs.getJobStatus(id);
				return ResponseEntity.ok(JobResponse.fromEntity(job));
		}

		@DeleteMapping("/jobs/{id}")
		public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
		    pjs.deleteJob(id); 
		    return ResponseEntity.noContent().build();
		}

		@GetMapping("/jobs/{id}/result")
		public ResponseEntity<Resource> downloadJobResult(@PathVariable Long id) {
				ProcessingJob job = pjs.getJobStatus(id);

				if (job.getStatus() != JobStatus.DONE || job.getResultPath() == null || job.getResultPath().isBlank()) {
						throw new ResourceNotFoundException("Processed file output is not available for Job ID: " + id);
				}

				Resource fileResource = ss.getResource(job.getResultPath());

				return ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getOutputName() + "\"")
						.contentType(MediaType.APPLICATION_OCTET_STREAM) // Safely fallback to binary streaming
						.body(fileResource);
		}
}
