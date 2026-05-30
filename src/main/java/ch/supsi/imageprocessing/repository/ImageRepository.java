package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
}
