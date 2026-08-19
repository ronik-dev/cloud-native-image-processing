package ch.supsi.imageprocessing.common.dto;

public record UserEventMessage(
    String username,
    String action // e.g., "DELETE"
) {}
