package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.exception.InvalidRequestException;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

		@Mock
		private UserRepository ur;

		@Mock
		private ImageService is; // required by deleteUser

		@InjectMocks
		private UserService us;

		// ==========================================
		// findOrCreateUser
		// ==========================================
		@Test
		void findOrCreateUser_ShouldReturnExisting_WhenUserExists() {
				String username = "username";
				String email = "username@mail.ch";
				User existingUser = new User(username, email);
				when(ur.findByUsername(username)).thenReturn(Optional.of(existingUser));

				User result = us.findOrCreateUser(username, email);

				assertNotNull(result);
				assertEquals(username, result.getUsername());
				verify(ur, never()).save(any(User.class)); // Should not save if it already exists
		}

		@Test
		void findOrCreateUser_ShouldCreate_WhenUserDoesNotExist() {
				String username = "username";
				String email = "username@mail.ch";
				when(ur.findByUsername(username)).thenReturn(Optional.empty());
				when(ur.save(any(User.class))).thenReturn(new User(username, email));

				User result = us.findOrCreateUser(username, email);

				assertNotNull(result);
				assertEquals(username, result.getUsername());
				verify(ur, times(1)).save(any(User.class)); // Should save a new user
		}

		@Test
		void findOrCreateUser_ShouldThrowInvalidRequestException_WhenUsernameIsNull() {
				InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> us.findOrCreateUser(null, "username@mail.ch"));
				assertEquals("Invalid or missing username.", ex.getMessage());
				verifyNoInteractions(ur);
		}

		@Test
		void findOrCreateUser_ShouldThrowInvalidRequestException_WhenUsernameIsEmpty() {
				InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> us.findOrCreateUser("", "username@mail.ch"));
				assertEquals("Invalid or missing username.", ex.getMessage());
				verifyNoInteractions(ur);
		}

		@Test
		void findOrCreateUser_ShouldThrowInvalidRequestException_WhenEmailIsNull() {
				InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> us.findOrCreateUser("username", null));
				assertEquals("Invalid or missing email.", ex.getMessage());
				verifyNoInteractions(ur);
		}

		@Test
		void findOrCreateUser_ShouldThrowInvalidRequestException_WhenEmailIsEmpty() {
				InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> us.findOrCreateUser("username", "   "));
				assertEquals("Invalid or missing email.", ex.getMessage());
				verifyNoInteractions(ur);
		}

		// ==========================================
		// getUserById
		// ==========================================
		@Test
		void getUserById_ShouldReturnUser_WhenUserExists() {
				Long userId = 1L;
				when(ur.findById(userId)).thenReturn(Optional.of(new User("username", "username@mail.ch")));

				User result = us.getUserById(userId);

				assertNotNull(result);
				assertEquals("username", result.getUsername());
				verify(ur, times(1)).findById(userId);
		}

		@Test
		void getUserById_ShouldThrowInvalidRequestException_WhenIdIsNull() {
				InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> us.getUserById(null));
				assertEquals("User ID cannot be null.", ex.getMessage());
				verifyNoInteractions(ur);
		}

		@Test
		void getUserById_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
				Long nonExistentId = 999L;
				when(ur.findById(nonExistentId)).thenReturn(Optional.empty());

				ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> us.getUserById(nonExistentId));

				assertEquals("User not found with ID: 999", ex.getMessage());
				verify(ur, times(1)).findById(nonExistentId);
		}

		// ==========================================
		// deleteUser
		// ==========================================
		//@Test
		//void deleteUser_ShouldCascadeImagesAndDeleteUser_WhenUserExists() {
		//		Long userId = 5L;
		//		User user = new User("username", "username@mail.ch");
		//		when(ur.findById(userId)).thenReturn(Optional.of(user));

		//		us.deleteUser(userId);

		//		// Images are purged before the user row is removed.
		//		verify(is, times(1)).deleteAllByUser(userId);
		//		verify(ur, times(1)).delete(user);
		//}

		//@Test
		//void deleteUser_ShouldThrow_WhenUserDoesNotExist() {
		//		when(ur.findById(5L)).thenReturn(Optional.empty());

		//		assertThrows(ResourceNotFoundException.class, () -> us.deleteUser(5L));

		//		verifyNoInteractions(is);
		//		verify(ur, never()).delete(any());
		//}
}
