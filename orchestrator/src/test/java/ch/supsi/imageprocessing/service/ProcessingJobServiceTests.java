package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.common.enums.JobType;
import ch.supsi.imageprocessing.common.dto.JobRequestMessage;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    @Mock
    private ProcessingJobRepository pjr;

    @Mock
    private KafkaTemplate<String, JobRequestMessage> jobRequestKafkaTemplate;

    @Mock
    private StorageService ss;

    @InjectMocks
    private ProcessingJobService processingJobService;

    private ProcessingJob newJob() {
        User user = new User("nicola", "nicola@supsi.ch");
        Image image = new Image("test.png", "/tmp/test.png", "png", user);
        return new ProcessingJob(image, JobType.FORMAT_CONVERSION, "output.png", "png");
    }

    @BeforeEach
    void setUp() {
        // Inject the @Value property for the Kafka topic manually for the tests
        ReflectionTestUtils.setField(processingJobService, "jobRequestsTopic", "job.requests");
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
    void processJob_ShouldSetStatusToRunningAndPublishMessage() {
        Long jobId = 100L;
        ProcessingJob job = newJob();
        // Use ReflectionTestUtils to set an ID, as it is needed for the Kafka key
        ReflectionTestUtils.setField(job, "id", jobId);

        when(pjr.findById(jobId)).thenReturn(Optional.of(job));
        when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock successful Kafka publish
        when(jobRequestKafkaTemplate.send(anyString(), anyString(), any(JobRequestMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ProcessingJob result = processingJobService.processJob(jobId);

        assertEquals(JobStatus.RUNNING, result.getStatus(), "Job status should be set to RUNNING immediately");
        assertNotNull(result.getTargetStorageKey(), "A new target storage key should have been generated.");
        verify(pjr, times(1)).save(job);
        verify(jobRequestKafkaTemplate, times(1)).send(eq("job.requests"), eq("100"), any(JobRequestMessage.class));
    }

    @Test
    void processJob_ShouldSetStatusToFailed_WhenKafkaPublishFails() {
        Long jobId = 100L;
        ProcessingJob job = newJob();
        ReflectionTestUtils.setField(job, "id", jobId);

        when(pjr.findById(jobId)).thenReturn(Optional.of(job));
        when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock a failed Kafka publish
        CompletableFuture<org.springframework.kafka.support.SendResult<String, JobRequestMessage>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker down"));
        
        when(jobRequestKafkaTemplate.send(anyString(), anyString(), any(JobRequestMessage.class)))
                .thenReturn(failedFuture);

        processingJobService.processJob(jobId);

        // Since the CompletableFuture is completed synchronously in this mock, the fallback fires immediately
        assertEquals(JobStatus.FAILED, job.getStatus(), "Job should be reverted to FAILED if publishing throws an exception");
        // Verify save was called twice: once to set RUNNING, once to fallback to FAILED
        verify(pjr, times(2)).save(job);
    }

    @Test
    void processJob_ShouldNotOverwriteExistingStorageKey() {
        Long jobId = 100L;
        ProcessingJob job = newJob();
        job.setTargetStorageKey("existing-key");
        when(pjr.findById(jobId)).thenReturn(Optional.of(job));
        when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobRequestKafkaTemplate.send(anyString(), anyString(), any(JobRequestMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

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
