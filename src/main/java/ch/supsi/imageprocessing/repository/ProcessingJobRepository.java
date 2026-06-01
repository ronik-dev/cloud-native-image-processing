package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.projection.JobSummary;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaRepository;

@RepositoryRestResource(excerptProjection = JobSummary.class)
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
}
