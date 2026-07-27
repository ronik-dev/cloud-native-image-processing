package ch.supsi.imageprocessing.common.dto;

import java.util.Map;

public record ErrorResponse(
    String timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors // Specifically for @Valid payload errors
) {}
