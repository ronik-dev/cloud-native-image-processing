package ch.supsi.imageprocessing.mapper;

import ch.supsi.imageprocessing.common.dto.UserResponse;
import ch.supsi.imageprocessing.entity.User;

public class UserMapper {

    private UserMapper() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail()
        );
    }
}
