package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.JobResponse;
import ch.supsi.imageprocessing.dto.ImageResponse;
import ch.supsi.imageprocessing.dto.JobRequest;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.validation.annotation.Validated;   

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/images")
@Validated
public class ImageController {

    @Autowired
    private ImageService is;

    @Autowired
    private UserService us;

    @PostMapping()
    public ResponseEntity<Void> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId") Long userId) {

        Image savedImage = is.handleImageUpload(us.getUserById(userId), file);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/images/{id}") // Point to the exact GET route
                .buildAndExpand(savedImage.getId())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageResponse> getImage(@PathVariable @Min(0) Long id) {
        Image image = is.getImageData(id);
        return ResponseEntity.ok(ImageResponse.fromEntity(image));
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
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.fromEntity(pj));
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<JobResponse>> getJobsByImage(@PathVariable @Min(0) Long id) {
        List<JobResponse> jobsr = is.getJobsByImage(id).stream()
                .map(JobResponse::fromEntity)
                .toList(); 
        return ResponseEntity.ok(jobsr);
    }
}
