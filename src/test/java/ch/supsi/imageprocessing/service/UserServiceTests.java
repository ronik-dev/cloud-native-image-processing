package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
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

    @InjectMocks
    private UserService us;

    // ==========================================
    // SUCCESS TESTS
    // ==========================================

    @Test
    void createUser_ShouldSucceed_WhenInputsAreValid() {
        String username = "username";
        String email = "username@mail.ch";
        User expectedUser = new User(username, email);

        when(ur.save(any(User.class))).thenReturn(expectedUser);

        User result = us.createUser(username, email);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals(email, result.getEmail());
        verify(ur, times(1)).save(any(User.class));
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        Long userId = 1L;
        User expectedUser = new User("username", "username@mail.ch");
        when(ur.findById(userId)).thenReturn(Optional.of(expectedUser));

        User result = us.getUserById(userId);

        assertNotNull(result);
        assertEquals("username", result.getUsername());
        verify(ur, times(1)).findById(userId);
    }

    // ==========================================
    // VALIDATION & FAILURE TESTS
    // ==========================================

    @Test
    void createUser_ShouldThrowInvalidRequestException_WhenUsernameIsEmpty() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> {
            us.createUser("", "username@mail.ch");
        });

        assertEquals("Invalid or missing username.", exception.getMessage());
        verifyNoInteractions(ur);
    }

    @Test
    void createUser_ShouldThrowInvalidRequestException_WhenEmailIsEmpty() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> {
            us.createUser("username", "   ");
        });

        assertEquals("Invalid or missing email.", exception.getMessage());
        verifyNoInteractions(ur);
    }

    @Test
    void getUserById_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        Long nonExistentId = 999L;
        when(ur.findById(nonExistentId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            us.getUserById(nonExistentId);
        });

        assertEquals("User not found with ID: 999", exception.getMessage());
        verify(ur, times(1)).findById(nonExistentId);
    }
}
