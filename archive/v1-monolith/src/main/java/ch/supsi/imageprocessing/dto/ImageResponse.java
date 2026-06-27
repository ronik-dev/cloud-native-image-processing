package ch.supsi.imageprocessing.dto;

import ch.supsi.imageprocessing.entity.Image;
import java.time.LocalDateTime;

public record ImageResponse(
    Long id,
    String filename,
    String format,
    LocalDateTime uploadedAt,
    Long userId
) {
    public static ImageResponse fromEntity(Image image) {
        return new ImageResponse(
            image.getId(),
            image.getName(),
            image.getFormat(),
            image.getUploadedAt(),
            image.getUser() != null ? image.getUser().getId() : null
        );
    }
}
