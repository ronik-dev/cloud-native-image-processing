package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class UserService {

		@Autowired
		private UserRepository ur;

		@Autowired
		private ImageRepository ir;

		@Autowired
		private ImageService is;

		@Transactional
		public User createUser(String username, String email) {
				if (username == null || username.strip().isEmpty()) {
						throw new InvalidRequestException("Invalid or missing username.");
				}
				if (email == null || email.strip().isEmpty()) {
						throw new InvalidRequestException("Invalid or missing email.");
				}

				return ur.save(new User(username, email));
		}

		@Transactional(readOnly = true)
		public User getUserById(Long id) {
				if (id == null) {
						throw new InvalidRequestException("User ID cannot be null.");
				}

				return ur.findById(id)
						.orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
		}

		@Transactional
		public void deleteUser(Long id){
				if (id == null) {
						throw new InvalidRequestException("User ID cannot be null.");
				}
				User user = ur.findById(id)
						.orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
				for(Image i: ir.findByUserId(id)){
						is.deleteImage(i.getId());	
				}
				ur.delete(user);
		}

		@Transactional(readOnly = true)
		public List<User> getAllUsers() {
				return ur.findAll();
		}
}
