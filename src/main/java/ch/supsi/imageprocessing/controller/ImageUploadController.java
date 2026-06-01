package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ImageUploadController {

		@Autowired
		private ImageService imageService;

		@Autowired
		private ImageRepository imageRepository;

		@Value("${storage.upload-dir:/tmp/imageprocessing/uploads}")
		private String uploadDirStr;

		@GetMapping("/images/list")
		public ResponseEntity<List<Image>> getAllImages() {
				// Uses standard JpaRepository findAll execution paths directly
				return ResponseEntity.ok(imageRepository.findAll());
		}

		@PostMapping("/upload")
		public ResponseEntity<Void> uploadFile(
				@RequestParam("file") MultipartFile file,
				@RequestParam(value = "userId", defaultValue = "1") Long userId) {

				if (file == null || file.isEmpty()) {
						throw new InvalidRequestException("Uploaded file cannot be empty.");
				}

				try {
						String originalFilename = file.getOriginalFilename();
						if (originalFilename == null || !originalFilename.contains(".")) {
								throw new InvalidRequestException("Invalid file name or missing extension.");
						}

						Path uploadPath = Paths.get(uploadDirStr);
						if (!Files.exists(uploadPath)) {
								Files.createDirectories(uploadPath);
						}

						Path destinationPath = uploadPath.resolve(originalFilename);
						String format = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

						Image savedImage = imageService.submitUpload(
										userId, 
										originalFilename, 
										destinationPath.toAbsolutePath().toString(), 
										format
										);

						Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

						URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
								.path("/images/{id}")
								.buildAndExpand(savedImage.getId())
								.toUri();

						return ResponseEntity.created(location).build();

				} catch (IOException e) {
						throw new RuntimeException("Disk I/O execution failure during copy", e);
				}
		}
}
