package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.ImageResponse;
import ch.supsi.imageprocessing.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ImageController {

    @Autowired
    private ImageRepository imageRepository;

    @GetMapping("/images/list")
    public ResponseEntity<List<ImageResponse>> getAllImages() {
        List<ImageResponse> responses = imageRepository.findAll().stream()
                .map(ImageResponse::fromEntity)
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(responses);
    }
}
