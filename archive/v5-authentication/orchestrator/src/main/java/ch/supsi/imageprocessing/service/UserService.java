package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.common.exception.InvalidRequestException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UserService {

		@Autowired
		private UserRepository ur;

		@Autowired
		private ImageService is;
		@Transactional(readOnly = true)
		public User getUserById(Long id) {
				if (id == null) {
						throw new InvalidRequestException("User ID cannot be null.");
				}

				return ur.findById(id)
						.orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
		}

		@Transactional
		public void deleteByUsername(String username) {
				ur.findByUsername(username).ifPresent(user -> {
						is.deleteAllByUser(user.getId());
						ur.delete(user);
				});
		}

		@Transactional
		public User findOrCreateUser(String username, String email) {
				if (username == null || username.strip().isEmpty()) {
						throw new InvalidRequestException("Invalid or missing username.");
				}
				if (email == null || email.strip().isEmpty()) {
						throw new InvalidRequestException("Invalid or missing email.");
				}

				return ur.findByUsername(username)
						.orElseGet(() -> ur.save(new User(username, email)));
		}
}
