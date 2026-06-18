package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.common.enums.JobType;
import ch.supsi.imageprocessing.common.dto.ConvertFormatRequest;
import ch.supsi.imageprocessing.common.dto.RemoveBackgroundRequest;
import ch.supsi.imageprocessing.common.dto.DetectObjectsRequest;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.client.WorkerClient;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;



import java.util.List;
import java.util.UUID;

@Service
public class ProcessingJobService {


		@Autowired
		private ProcessingJobRepository pjr;

		@Autowired
		private WorkerClient wc;

		@Autowired
		private StorageService ss;

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

				return pjr.save(job);
		}

		@Async
		public void startAsyncProcessExecution(Long jobId) {
				ProcessingJob job = pjr.findById(jobId)
						.orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
				try {
						job.setStatus(JobStatus.RUNNING);
						pjr.save(job);

						switch (job.getType()) {
								case JobType.FORMAT_CONVERSION :wc.convertFormat(
										new ConvertFormatRequest(
												job.getImage().getStorageKey(),
												job.getImage().getFormat(),
												job.getTargetStorageKey(),
												job.getTargetFormat()
												)
										);
										break;
								case JobType.BACKGROUND_REMOVAL :wc.removeBackground(
									   	new RemoveBackgroundRequest(
												job.getImage().getStorageKey(),
												job.getTargetStorageKey()
												)
									   	);
										break;
								case JobType.OBJECT_DETECTION:wc.detectObjects(
									   	new DetectObjectsRequest(
												job.getImage().getStorageKey(),
												job.getTargetStorageKey()
												)
									   	);
										break;
								default :throw new IllegalArgumentException("Undefined JobType: " + job.getType());
						};

						job.setStatus(JobStatus.DONE);

				} catch (Exception e) {
						log.error("Processing failed for job {}: {}", jobId, e.getMessage(), e);
						job.setStatus(JobStatus.FAILED);
				} finally {
						pjr.save(job);
				}
		}

		@Transactional
		private void executeProcess(ProcessingJob job){

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

