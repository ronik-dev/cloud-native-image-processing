package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.common.dto.UserRequest;
import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;
import ch.supsi.imageprocessing.mapper.UserMapper;
import ch.supsi.imageprocessing.mapper.ImageMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;   
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;   

import java.util.List;

@RestController
@RequestMapping("/users")
@Validated
public class UserController{

		@Autowired
		private ImageService is;

		@Autowired
		private UserService us;

		@GetMapping("/{id}/images")
		public ResponseEntity<List<ImageResponse>> getImagesByUser(@PathVariable @Min(0) Long id) {
				List<ImageResponse> responses = is.getImagesByUser(us.getUserById(id)).stream() 
						.map(ImageMapper::toResponse)
						.toList();
				return ResponseEntity.ok(responses);
		}

		@PostMapping("/find-or-create")
		public ResponseEntity<UserResponse> findOrCreateUser(@Valid @RequestBody UserRequest request) {
				User u = us.findOrCreateUser(request.username(), request.email());
				return ResponseEntity.ok(UserMapper.toResponse(u));
		}
}
