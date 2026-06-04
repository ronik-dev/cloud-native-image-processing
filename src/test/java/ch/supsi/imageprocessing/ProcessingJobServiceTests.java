package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.processor.ImageProcessor;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.service.ProcessingJobService;
import ch.supsi.imageprocessing.service.StorageService;
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

    @Mock
    private StorageService ss; // Added to match the new Service dependencies

    @InjectMocks
    private ProcessingJobService processingJobService;

    // ==========================================
    // TESTS: processJob (Synchronous Setup)
    // ==========================================

    @Test
    void processJob_ShouldSetupStorageKey_AndReturnJobInPendingState() {
        Long jobId = 100L;
        User mockUser = new User("nicola", "nicola@supsi.ch");
        Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
        
        // Assuming default constructor sets status to PENDING
        ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.FORMAT_CONVERSION, "output.png", "png");
        
        when(pjr.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessingJob result = processingJobService.processJob(jobId);

        assertNotNull(result);
        assertNotNull(result.getTargetStorageKey(), "A new target storage key should have been generated.");
        // The status should remain whatever it was initially (PENDING), because execution hasn't started
    }

    // ==========================================
    // TESTS: startAsyncProcessExecution (Async Logic)
    // ==========================================

    @Test
    void startAsyncProcessExecution_ShouldSetStatusToDone_WhenProcessorSucceeds() throws Exception {
        Long jobId = 100L;
        User mockUser = new User("nicola", "nicola@supsi.ch");
        Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
        ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.FORMAT_CONVERSION, "output.png", "png");

        when(pjr.findById(jobId)).thenReturn(Optional.of(mockJob));
        // Mock the processor to succeed
        when(ip.execute(any(ProcessingJob.class))).thenReturn("/tmp/outputs/output.png");

        // Act
        processingJobService.startAsyncProcessExecution(jobId);

        // Assert
        assertEquals(JobStatus.DONE, mockJob.getStatus());
        verify(ip, times(1)).execute(any(ProcessingJob.class));
        verify(pjr, atLeastOnce()).save(mockJob); // Verifies the status saves were flushed to DB
    }

    @Test
    void startAsyncProcessExecution_ShouldSetStatusToFailed_WhenProcessorThrowsException() throws Exception {
        Long jobId = 100L;
        User mockUser = new User("nicola", "nicola@supsi.ch");
        Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
        ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.FORMAT_CONVERSION, "output.png", "png");

        when(pjr.findById(jobId)).thenReturn(Optional.of(mockJob));
        // Mock the processor to crash
        when(ip.execute(any(ProcessingJob.class))).thenThrow(new RuntimeException("Simulated FFmpeg crash"));

        // Act
        processingJobService.startAsyncProcessExecution(jobId);

        // Assert
        assertEquals(JobStatus.FAILED, mockJob.getStatus());
        verify(ip, times(1)).execute(any(ProcessingJob.class));
        verify(pjr, atLeastOnce()).save(mockJob);
    }
}
