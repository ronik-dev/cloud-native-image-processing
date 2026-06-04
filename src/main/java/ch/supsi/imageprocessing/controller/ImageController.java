package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.JobResponse;
import ch.supsi.imageprocessing.dto.JobRequest;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ImageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;   

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@Validated
public class ImageController {

		@Autowired
		private ImageService is;

		@PostMapping("/{id}/jobs")
		public ResponseEntity<JobResponse> createJob(@PathVariable @Min(0) Long id,@Valid @RequestBody JobRequest request) {
		        ProcessingJob pj = is.createJob(id, request);
		        return ResponseEntity.accepted().body(JobResponse.fromEntity(pj));
		}

		@GetMapping("/{id}/jobs")
		public ResponseEntity<List<JobResponse>> getJobsByImage(@PathVariable @Min(0) Long id) {
				List<JobResponse> jobs = is.getJobsByImage(id).stream()
						.map(JobResponse::fromEntity)
						.toList(); 
				return ResponseEntity.ok(jobs);
		}

		@DeleteMapping("/{id}")
		public ResponseEntity<Void> deleteImage(@PathVariable @Min(0) Long id) {
				is.deleteImage(id); 
				return ResponseEntity.noContent().build();
		}
}
