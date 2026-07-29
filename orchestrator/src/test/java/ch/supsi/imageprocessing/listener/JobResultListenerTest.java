package ch.supsi.imageprocessing.listener;

import ch.supsi.imageprocessing.common.dto.JobResultMessage;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * No Kafka broker involved: onJobResult is called directly,
 * same approach as the existing *ServiceTests classes.
 *
 * ProcessingJob is mocked rather than constructed via `new ProcessingJob(...)`
 * since these tests care about the listener's branching (found/not-found,
 * DONE/FAILED), not entity/association behavior.
 */
@ExtendWith(MockitoExtension.class)
class JobResultListenerTests {

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @InjectMocks
    private JobResultListener jobResultListener;

    @Test
    void onJobResult_whenStatusDone_setsStatusDoneAndSaves() {
        ProcessingJob job = mock(ProcessingJob.class);
        when(processingJobRepository.findById(42L)).thenReturn(Optional.of(job));

        JobResultMessage message = new JobResultMessage(42L, "DONE", "target-sk-1", null);

        jobResultListener.onJobResult(message);

        verify(job).setStatus(JobStatus.DONE);
        verify(processingJobRepository).save(job);
    }

    @Test
    void onJobResult_whenStatusFailed_setsStatusFailedAndSaves() {
        ProcessingJob job = mock(ProcessingJob.class);
        when(processingJobRepository.findById(42L)).thenReturn(Optional.of(job));

        JobResultMessage message = new JobResultMessage(42L, "FAILED", null, "rembg crashed");

        jobResultListener.onJobResult(message);

        verify(job).setStatus(JobStatus.FAILED);
        verify(processingJobRepository).save(job);
    }

    @Test
    void onJobResult_whenJobNotFound_doesNotSaveAnything() {
        when(processingJobRepository.findById(99L)).thenReturn(Optional.empty());

        JobResultMessage message = new JobResultMessage(99L, "DONE", "target-sk-2", null);

        jobResultListener.onJobResult(message);

        verify(processingJobRepository, never()).save(any());
    }

    @Test
    void onJobResult_isIdempotentUnderRedelivery() {
        // At-least-once Kafka delivery means this listener can run twice for
        // the same message (sprint-3, §9.2). Calling it twice with the same
        // payload should just re-apply the same status, not error or double-save
        // in a way that matters.
        ProcessingJob job = mock(ProcessingJob.class);
        when(processingJobRepository.findById(42L)).thenReturn(Optional.of(job));

        JobResultMessage message = new JobResultMessage(42L, "DONE", "target-sk-1", null);

        jobResultListener.onJobResult(message);
        jobResultListener.onJobResult(message);

        verify(job, times(2)).setStatus(JobStatus.DONE);
        verify(processingJobRepository, times(2)).save(job);
    }
}
