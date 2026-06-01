package ch.supsi.imageprocessing.projection;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.entity.JobType;
import org.springframework.data.rest.core.config.Projection;

@Projection(name = "jobSummary", types = ProcessingJob.class)
public interface JobSummary {

		Long getId();

		JobType getType();

		JobStatus getStatus();

		String getOutputName();
}
