package ch.supsi.imageprocessing.dto;

import ch.supsi.imageprocessing.entity.User;

public record UserResponse(
    Long id,
    String username,
    String email
) {
    public static UserResponse fromEntity(User u) {
        return new UserResponse(
            u.getId(),
            u.getUsername(),
            u.getEmail()
        );
    }
}
