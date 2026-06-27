package ch.supsi.imageprocessing.common.dto;


public record UserResponse(
    Long id,
    String username,
    String email
) {}
