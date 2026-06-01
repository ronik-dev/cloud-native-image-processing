package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.projection.ImageSummary;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;

@RepositoryRestResource(excerptProjection = ImageSummary.class)
public interface ImageRepository extends JpaRepository<Image, Long> {
}
