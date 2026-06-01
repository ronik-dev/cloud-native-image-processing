package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.ImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProcessJobController{

    @Autowired
    private ImageService imageService;

    @Value("${storage.upload-dir:/tmp/imageprocessing/uploads}")
    private String uploadDirStr;

    @PostMapping("/jobs/{id}/process")
    public ResponseEntity<ProcessingJob> triggerProcessing(@PathVariable Long id) {
        ProcessingJob updatedJob = imageService.processJob(id);
        return ResponseEntity.ok(updatedJob);
    }
}
