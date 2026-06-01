package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.file.Path;

@RestController
@RequestMapping("/api")
public class ImageUploadController {

    @Autowired
    private StorageService storageService;

    @Autowired
    private ImageService imageService;

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        Path targetPath = storageService.store(file);
        String filename = file.getOriginalFilename();
        String format = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        Image savedImage = imageService.submitUpload(userId, filename, targetPath.toString(), format);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/images/{id}")
                .buildAndExpand(savedImage.getId())
                .toUri();

        return ResponseEntity.created(location).build();
    }
}
