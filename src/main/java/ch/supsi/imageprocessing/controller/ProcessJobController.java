package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.JobResponse;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProcessJobController{

    @Autowired
    private ImageService imageService;

    @PostMapping("/jobs/{id}/process")
    public ResponseEntity<JobResponse> triggerProcessing(@PathVariable Long id) {
        ProcessingJob updatedJob = imageService.processJob(id);
        return ResponseEntity.ok(JobResponse.fromEntity(updatedJob));
    }
}
