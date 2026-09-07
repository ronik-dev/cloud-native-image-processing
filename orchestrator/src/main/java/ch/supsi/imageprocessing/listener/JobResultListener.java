package ch.supsi.imageprocessing.listener;

import ch.supsi.imageprocessing.common.dto.JobResultMessage;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the old synchronous try/catch/finally block that used to live in
 * ProcessingJobService.startAsyncProcessExecution. The worker now reports its
 * own outcome asynchronously via job.results instead of the orchestrator
 * blocking on a WorkerClient HTTP call.
 */
@Component
public class JobResultListener {

    private static final Logger log = LoggerFactory.getLogger(JobResultListener.class);
    private final ProcessingJobRepository pjr;

    public JobResultListener(ProcessingJobRepository pjr) {
        this.pjr = pjr;
    }

    @KafkaListener(topics = "${kafka.topics.job-results}", groupId = "orchestrator-job-results")
    @Transactional
    public void onJobResult(JobResultMessage message) {
        ProcessingJob job = pjr.findById(message.jobId()).orElse(null);
        if (job == null) {
            // Job was deleted (cascade-delete on user/image) while a result was
            // in flight. Nothing to update — this is not an error.
            log.warn("Received result for unknown/deleted job {}", message.jobId());
            return;
        }

        // Kafka delivery is at-least-once: this listener may run twice for the
        // same job. Both branches are idempotent (re-setting the same status
        // and target key), so no dedup guard is needed here.
        if ("DONE".equals(message.status())) {
            job.setStatus(JobStatus.DONE);
        } else {
            job.setStatus(JobStatus.FAILED);
            log.error("Job {} failed in worker: {}", message.jobId(), message.errorMessage());
        }
        pjr.save(job);
    }
}
