package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
}
