package ch.supsi.imageprocessing.common.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank(message = "Username cannot be empty")
        @Size(min = 1, max = 50, message = "Username must be between 1 and 50 characters")
        String username,

        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Email must be a valid email format")
        @Size(max = 255, message = "Email is too long")
        String email
) {}
