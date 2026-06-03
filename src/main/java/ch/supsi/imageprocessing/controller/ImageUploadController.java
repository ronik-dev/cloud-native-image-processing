package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class ImageUploadController {

    @Autowired
    private ImageService is;

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        Image savedImage = is.handleImageUpload(userId, file);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/images/{id}")
                .buildAndExpand(savedImage.getId())
                .toUri();

        return ResponseEntity.created(location).build();
    }
}
