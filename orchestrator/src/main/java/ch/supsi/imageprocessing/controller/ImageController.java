package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.common.dto.JobRequest;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;
import ch.supsi.imageprocessing.mapper.ImageMapper;
import ch.supsi.imageprocessing.mapper.JobMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;   

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/images")
@Validated
public class ImageController {

    @Autowired
    private ImageService is;

    @Autowired
    private UserService us;

		@PostMapping
		public ResponseEntity<ImageResponse> uploadFile(
		        @RequestParam("file") MultipartFile file,
		        @RequestParam("userId") Long userId) {
		    Image savedImage = is.handleImageUpload(us.getUserById(userId), file);
		    return ResponseEntity.status(HttpStatus.CREATED).body(ImageMapper.toResponse(savedImage));
		}

    @GetMapping("/{id}")
    public ResponseEntity<ImageResponse> getImage(@PathVariable @Min(0) Long id) {
        Image image = is.getImageData(id);
        return ResponseEntity.ok(ImageMapper.toResponse(image));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable @Min(0) Long id) {
        is.deleteImage(id); 
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/jobs")
    public ResponseEntity<JobResponse> createJob(
            @PathVariable @Min(0) Long id,
            @Valid @RequestBody JobRequest request) {
        ProcessingJob pj = is.createJob(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(JobMapper.toResponse(pj));
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<JobResponse>> getJobsByImage(@PathVariable @Min(0) Long id) {
        List<JobResponse> jobsr = is.getJobsByImage(id).stream()
                .map(JobMapper::toResponse)
                .toList(); 
        return ResponseEntity.ok(jobsr);
    }
}
