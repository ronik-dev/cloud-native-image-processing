package ch.supsi.imageprocessing.common.dto;

import java.time.LocalDateTime;

public record ImageResponse(
    Long id,
    String filename,
    String format,
    LocalDateTime uploadedAt,
    Long userId
){}
