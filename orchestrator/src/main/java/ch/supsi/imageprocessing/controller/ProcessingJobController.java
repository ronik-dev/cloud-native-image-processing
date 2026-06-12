package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ProcessingJobService;
import ch.supsi.imageprocessing.mapper.JobMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;   

import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/jobs")
@Validated
public class ProcessingJobController{


		@Autowired
		private ProcessingJobService pjs;

		@PostMapping("/{id}/process")
		public ResponseEntity<JobResponse> triggerProcessing(@PathVariable @Min(0) Long id) {
				ProcessingJob pj = pjs.processJob(id);
				pjs.startAsyncProcessExecution(pj.getId());
				return ResponseEntity.accepted().body(JobMapper.toResponse(pj));
		}

		@GetMapping("/{id}")
		public ResponseEntity<JobResponse> getJobStatus(@PathVariable @Min(0) Long id) {
				ProcessingJob pj = pjs.getJobStatus(id);
				return ResponseEntity.ok(JobMapper.toResponse(pj));
		}

		@DeleteMapping("/{id}")
		public ResponseEntity<Void> deleteJob(@PathVariable @Min(0) Long id) {
		    pjs.deleteJob(id); 
		    return ResponseEntity.noContent().build();
		}

		@GetMapping("/{id}/result")
		    public ResponseEntity<Resource> downloadJobResult(@PathVariable @Min(0) Long id) {
		        Resource file = pjs.getJobResult(id);
		        ProcessingJob job = pjs.getJobStatus(id);
		        return ResponseEntity.ok()
		                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getOutputName() + "\"")
		                .contentType(MediaType.APPLICATION_OCTET_STREAM)
		                .body(file);
		    }
}
