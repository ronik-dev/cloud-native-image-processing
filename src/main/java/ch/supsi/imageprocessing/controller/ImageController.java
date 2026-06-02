package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.ImageResponse;
import ch.supsi.imageprocessing.dto.JobResponse;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.ProcessingJobService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ImageController {

		@Autowired
		private ImageService is;

		@Autowired
		private ProcessingJobService pjs;

		@GetMapping("/users/{userId}/images")
		public ResponseEntity<List<ImageResponse>> getImagesByUser(@PathVariable Long userId) {
		    List<ImageResponse> responses = is.getAllImages().stream()
		            .filter(img -> img.getUser() != null && img.getUser().getId().equals(userId))
		            .map(ImageResponse::fromEntity)
		            .toList();
		
		    return ResponseEntity.ok(responses);
		}

		@PostMapping("/images/{imageId}/newjob")
		public ResponseEntity<JobResponse> createNewJob(
						@PathVariable Long imageId,
						@RequestParam(value = "outputName", defaultValue = "processed_output") String outputName,
						@RequestParam(value = "targetFormat", defaultValue = "png") String targetFormat){

				 ProcessingJob pj = is.createConversionJob(imageId, outputName, targetFormat);
   				 
   				 ProcessingJob processedJob = pjs.processJob(pj.getId());
   				 
   				 return ResponseEntity.ok(JobResponse.fromEntity(processedJob));
		}

		@GetMapping("/images/{imageId}/jobs")
		public ResponseEntity<List<JobResponse>> getJobsByImage(@PathVariable Long imageId) {
				List<JobResponse> jobs = is.getJobsByImage(imageId).stream()
						.map(JobResponse::fromEntity)
						.toList(); 
				return ResponseEntity.ok(jobs);
		}

		@DeleteMapping("/images/{imageId}")
		public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
				is.deleteImage(imageId); 
				return ResponseEntity.noContent().build();
		}
}
