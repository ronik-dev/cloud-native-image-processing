package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.processor.ImageProcessor;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.util.List;
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

		@Mock
		private StorageService ss;

		@InjectMocks
		private ProcessingJobService processingJobService;

		private ProcessingJob newJob() {
				User user = new User("nicola", "nicola@supsi.ch");
				Image image = new Image("test.png", "/tmp/test.png", "png", user);
				return new ProcessingJob(image, JobType.FORMAT_CONVERSION, "output.png", "png");
		}

		// ==========================================
		// getAllJobs
		// ==========================================

		@Test
		void getAllJobs_ShouldDelegateToRepository() {
				List<ProcessingJob> all = List.of(newJob());
				when(pjr.findAll()).thenReturn(all);
				assertEquals(all, processingJobService.getAllJobs());
		}

		// ==========================================
		// processJob
		// ==========================================

		@Test
		void processJob_ShouldGenerateStorageKey_WhenMissing() {
				Long jobId = 100L;
				ProcessingJob job = newJob();
				when(pjr.findById(jobId)).thenReturn(Optional.of(job));
				when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

				ProcessingJob result = processingJobService.processJob(jobId);

				assertNotNull(result.getTargetStorageKey(), "A new target storage key should have been generated.");
				verify(pjr).save(job);
		}

		@Test
		void processJob_ShouldNotOverwriteExistingStorageKey() {
				Long jobId = 100L;
				ProcessingJob job = newJob();
				job.setTargetStorageKey("existing-key");
				when(pjr.findById(jobId)).thenReturn(Optional.of(job));
				when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

				ProcessingJob result = processingJobService.processJob(jobId);

				assertEquals("existing-key", result.getTargetStorageKey());
		}

		@Test
		void processJob_ShouldThrow_WhenJobDoesNotExist() {
				when(pjr.findById(100L)).thenReturn(Optional.empty());
				assertThrows(ResourceNotFoundException.class, () -> processingJobService.processJob(100L));
				verify(pjr, never()).save(any());
		}

		// ==========================================
		// startAsyncProcessExecution
		// ==========================================

		@Test
		void startAsyncProcessExecution_ShouldSetStatusToDone_WhenProcessorSucceeds() throws Exception {
				Long jobId = 100L;
				ProcessingJob job = newJob();
				when(pjr.findById(jobId)).thenReturn(Optional.of(job));
				when(ip.execute(any(ProcessingJob.class))).thenReturn("/tmp/outputs/output.png");

				processingJobService.startAsyncProcessExecution(jobId);

				assertEquals(JobStatus.DONE, job.getStatus());
				verify(ip, times(1)).execute(any(ProcessingJob.class));
				verify(pjr, atLeastOnce()).save(job);
		}

		@Test
		void startAsyncProcessExecution_ShouldSetStatusToFailed_WhenProcessorThrows() throws Exception {
				Long jobId = 100L;
				ProcessingJob job = newJob();
				when(pjr.findById(jobId)).thenReturn(Optional.of(job));
				when(ip.execute(any(ProcessingJob.class))).thenThrow(new RuntimeException("Simulated FFmpeg crash"));

				processingJobService.startAsyncProcessExecution(jobId);

				assertEquals(JobStatus.FAILED, job.getStatus());
				verify(ip, times(1)).execute(any(ProcessingJob.class));
				verify(pjr, atLeastOnce()).save(job);
		}

		@Test
		void startAsyncProcessExecution_ShouldThrow_WhenJobDoesNotExist() {
				when(pjr.findById(404L)).thenReturn(Optional.empty());
				assertThrows(ResourceNotFoundException.class,
								() -> processingJobService.startAsyncProcessExecution(404L));
		}

		// ==========================================
		// getJobStatus
		// ==========================================

		@Test
		void getJobStatus_ShouldReturnJob_WhenItExists() {
				ProcessingJob job = newJob();
				when(pjr.findById(1L)).thenReturn(Optional.of(job));
				assertSame(job, processingJobService.getJobStatus(1L));
		}

		@Test
		void getJobStatus_ShouldThrow_WhenJobDoesNotExist() {
				when(pjr.findById(1L)).thenReturn(Optional.empty());
				assertThrows(ResourceNotFoundException.class, () -> processingJobService.getJobStatus(1L));
		}

		// ==========================================
		// deleteJob
		// ==========================================

		@Test
		void deleteJob_ShouldDeleteOutputResource_WhenJobIsDone() {
				ProcessingJob job = newJob();
				job.setStatus(JobStatus.DONE);
				job.setTargetStorageKey("output-key");
				when(pjr.findById(1L)).thenReturn(Optional.of(job));

				processingJobService.deleteJob(1L);

				verify(ss, times(1)).deleteResource("output-key");
				verify(pjr, times(1)).delete(job);
		}

		@Test
		void deleteJob_ShouldNotTouchStorage_WhenJobIsNotDone() {
				ProcessingJob job = newJob(); // PENDING
				when(pjr.findById(1L)).thenReturn(Optional.of(job));

				processingJobService.deleteJob(1L);

				verifyNoInteractions(ss);
				verify(pjr, times(1)).delete(job);
		}

		@Test
		void deleteJob_ShouldThrow_WhenJobDoesNotExist() {
				when(pjr.findById(1L)).thenReturn(Optional.empty());
				assertThrows(ResourceNotFoundException.class, () -> processingJobService.deleteJob(1L));
				verify(pjr, never()).delete(any());
		}

		// ==========================================
		// getJobsByImage
		// ==========================================

		@Test
		void getJobsByImage_ShouldDelegateToRepositoryByImageId() {
				Image image = new Image("a", "k", "png", new User("u", "u@e.ch"));
				List<ProcessingJob> jobs = List.of(newJob());
				when(pjr.findByImageId(image.getId())).thenReturn(jobs);

				assertEquals(jobs, processingJobService.getJobsByImage(image));
		}

		// ==========================================
		// getJobResult
		// ==========================================

		@Test
		void getJobResult_ShouldReturnResource_WhenJobIsDoneWithKey() {
				ProcessingJob job = newJob();
				job.setStatus(JobStatus.DONE);
				job.setTargetStorageKey("result-key");
				Resource resource = mock(Resource.class);
				when(pjr.findById(1L)).thenReturn(Optional.of(job));
				when(ss.getResource("result-key")).thenReturn(resource);

				assertSame(resource, processingJobService.getJobResult(1L));
		}

		@Test
		void getJobResult_ShouldThrow_WhenJobDoesNotExist() {
				when(pjr.findById(1L)).thenReturn(Optional.empty());
				assertThrows(ResourceNotFoundException.class, () -> processingJobService.getJobResult(1L));
				verifyNoInteractions(ss);
		}

		@Test
		void getJobResult_ShouldThrow_WhenJobIsNotDone() {
				ProcessingJob job = newJob(); // PENDING
				job.setTargetStorageKey("result-key");
				when(pjr.findById(1L)).thenReturn(Optional.of(job));

				assertThrows(ResourceNotFoundException.class, () -> processingJobService.getJobResult(1L));
				verifyNoInteractions(ss);
		}

		@Test
		void getJobResult_ShouldThrow_WhenStorageKeyIsBlank() {
				ProcessingJob job = newJob();
				job.setStatus(JobStatus.DONE);
				job.setTargetStorageKey("   ");
				when(pjr.findById(1L)).thenReturn(Optional.of(job));

				assertThrows(ResourceNotFoundException.class, () -> processingJobService.getJobResult(1L));
				verifyNoInteractions(ss);
		}
}
