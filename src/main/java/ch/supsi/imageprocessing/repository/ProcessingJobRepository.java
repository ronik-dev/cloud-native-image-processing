package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.ProcessingJob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
		List<ProcessingJob> findByImageId(Long imageId);
}
