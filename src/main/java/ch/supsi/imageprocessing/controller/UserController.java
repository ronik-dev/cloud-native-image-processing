package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.ImageResponse;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;   


import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController{

		@Autowired
		private ImageService is;

		@Autowired
		private UserService us;

		@GetMapping("/{id}/images")
		public ResponseEntity<List<ImageResponse>> getImagesByUser(@PathVariable @Min(0) Long id) {
				List<ImageResponse> responses = is.getAllImages().stream()
						.filter(img -> img.getUser() != null && img.getUser().getId().equals(id))
						.map(ImageResponse::fromEntity)
						.toList();

				return ResponseEntity.ok(responses);
		}

		@DeleteMapping("/{id}")
		public ResponseEntity<Void> deleteImage(@PathVariable @Min(0) Long id) {
				us.deleteUser(id); 
				return ResponseEntity.noContent().build();
		}
}
