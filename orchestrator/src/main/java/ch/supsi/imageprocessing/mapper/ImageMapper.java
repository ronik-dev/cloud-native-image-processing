package ch.supsi.imageprocessing.mapper;

import ch.supsi.imageprocessing.common.dto.ImageResponse;
import ch.supsi.imageprocessing.entity.Image;

public class ImageMapper {
    public static ImageResponse toResponse(Image image) {
        return new ImageResponse(
            image.getId(),
            image.getName(),
            image.getFormat(),
            image.getUploadedAt(),
            image.getUser() != null ? image.getUser().getId() : null
        );
    }
}
