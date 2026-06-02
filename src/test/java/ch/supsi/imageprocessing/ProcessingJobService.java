package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.processor.ImageProcessor;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.service.ProcessingJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

		@Mock
		private ProcessingJobRepository pjr;

		@Mock
		private ImageProcessor ip;

		@InjectMocks
		private ProcessingJobService processingJobService;

		@Test
		void processJob_ShouldSetStatusToDone_WhenProcessorSucceeds() throws Exception {
				Long jobId = 100L;
				User mockUser = new User("nicola", "nicola@supsi.ch");
				Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
				ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.FORMAT_CONVERSION, "output.png", "png");

				when(pjr.findById(jobId)).thenReturn(Optional.of(mockJob));
				when(pjr.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));
				when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));
				when(ip.execute(any(ProcessingJob.class))).thenReturn("/tmp/outputs/output.png");

				ProcessingJob result = processingJobService.processJob(jobId);

				assertNotNull(result);
				assertEquals(JobStatus.DONE, result.getStatus());
				assertEquals("/tmp/outputs/output.png", result.getResultPath());
				verify(ip, times(1)).execute(any(ProcessingJob.class));
		}

		@Test
		void processJob_ShouldSetStatusToFailed_WhenProcessorThrowsException() throws Exception {
				Long jobId = 100L;
				User mockUser = new User("nicola", "nicola@supsi.ch");
				Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
				ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.FORMAT_CONVERSION, "output.png", "png");

				when(pjr.findById(jobId)).thenReturn(Optional.of(mockJob));
				when(pjr.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));
				when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));
				when(ip.execute(any(ProcessingJob.class))).thenThrow(new RuntimeException("Processing failed"));

				ProcessingJob result = processingJobService.processJob(jobId);

				assertNotNull(result);
				assertEquals(JobStatus.FAILED, result.getStatus());
		}
}
