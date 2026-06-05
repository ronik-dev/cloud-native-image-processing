package ch.supsi.imageprocessing.dto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Email;



public record UserRequest(
    @NotEmpty(message = "Username cannot be empty")
    String username,

	@Email(message= "Email must be a valid email")
    @NotEmpty(message = "Username cannot be empty")
    String email
) {
}
