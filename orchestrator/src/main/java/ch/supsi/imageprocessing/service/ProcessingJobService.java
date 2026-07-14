package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.common.dto.JobRequestMessage;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.kafka.core.KafkaTemplate;   
import org.springframework.beans.factory.annotation.Value;   

import java.util.List;
import java.util.UUID;   

@Service
public class ProcessingJobService {

		@Autowired
		private ProcessingJobRepository pjr;

		@Autowired
		private StorageService ss;


		@Autowired
		private KafkaTemplate<String, JobRequestMessage> jobRequestKafkaTemplate;

		@Value("${kafka.topics.job-requests}")
		private String jobRequestsTopic;

		private static final Logger log = LoggerFactory.getLogger(ProcessingJobService.class);


		@Transactional(readOnly = true)
		public List<ProcessingJob> getAllJobs() {
				return pjr.findAll();
		}

		@Transactional
		public ProcessingJob processJob(Long jobId) {
				ProcessingJob job = pjr.findById(jobId)
						.orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
				if (job.getTargetStorageKey() == null || job.getTargetStorageKey().isBlank()) {
						String newStorageKey = UUID.randomUUID().toString();
						job.setTargetStorageKey(newStorageKey);
				}
				job.setStatus(JobStatus.RUNNING);
				pjr.save(job);
				publishJobRequest(job);
				// Returned immediately with status RUNNING — the controller still
				// responds 202 Accepted, same contract as before. The client polls
				// GET /api/jobs/{id} as usual; JobResultListener is what eventually
				// flips it to DONE/FAILED.
				return job;
		}

		private void publishJobRequest(ProcessingJob job) {
				JobRequestMessage message = new JobRequestMessage(
								job.getId(),
								job.getType(),
								job.getImage().getStorageKey(),
								job.getTargetStorageKey(),
								job.getImage().getFormat(),
								job.getTargetFormat()
								);
		// Key by jobId so every message for this job lands on the same partition, in order, even if a job is ever re-submitted.
		jobRequestKafkaTemplate.send(jobRequestsTopic, String.valueOf(job.getId()), message)
				.whenComplete((result, ex) -> {
						if (ex != null) {
								log.error("Failed to publish job request for job {}: {}", job.getId(), ex.getMessage(), ex);
								// Best-effort compensation: the worker will never see this
								// job, so flip it back to FAILED rather than leaving it
								// stuck in RUNNING forever. A transactional outbox is the
								// more robust fix if this gap matters for your thesis
								// scope — worth a line in the "limitations" section.
								job.setStatus(JobStatus.FAILED);
								pjr.save(job);
						}
				});
		}

		@Transactional(readOnly = true)
		public ProcessingJob getJobStatus(Long jobId) {
				return pjr.findById(jobId)
						.orElseThrow(() -> new ResourceNotFoundException("Job not found with ID: " + jobId));
		}

		@Transactional
		public void deleteJob(Long jobId){
				ProcessingJob job = pjr.findById(jobId)
						.orElseThrow(()->new ResourceNotFoundException("Job not found with ID: " + jobId));
				if (job.getStatus()==JobStatus.DONE){
						ss.deleteResource(job.getTargetStorageKey());
				}
				pjr.delete(job);
		}

		@Transactional(readOnly = true)
		public List<ProcessingJob> getJobsByImage(Image image) {
				return pjr.findByImageId(image.getId());
		}

		@Transactional(readOnly = true)
		public Resource getJobResult(Long jobId) {
				ProcessingJob job = pjr.findById(jobId)
						.orElseThrow(() -> new ResourceNotFoundException("Job not found with ID: " + jobId));

				if (job.getStatus() != JobStatus.DONE
				    || job.getTargetStorageKey() == null
					|| job.getTargetStorageKey().isBlank()) {
						throw new ResourceNotFoundException("Processed file output is not available for Job ID: " + jobId);
				}

				return ss.getResource(job.getTargetStorageKey());
		}
}

