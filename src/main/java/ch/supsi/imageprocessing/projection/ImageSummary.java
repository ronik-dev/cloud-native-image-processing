package ch.supsi.imageprocessing.projection;

import ch.supsi.imageprocessing.entity.Image;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;
import java.time.LocalDateTime;
import java.util.List;

@Projection(name = "summary", types = Image.class)
public interface ImageSummary {

		@Value("#{target.name}")
		String getFilename();

		LocalDateTime getUploadedAt();

		List<JobSummary> getProcessingJobs();
}
