package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UserService {

		@Autowired
		private UserRepository ur;

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
		public User getUserById(Long userId) {
				if (userId == null) {
						throw new InvalidRequestException("User ID cannot be null.");
				}

				return ur.findById(userId)
						.orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
		}
}
