package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.processor.ImageProcessor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@Service
public class ProcessingJobService {


		@Autowired
		private ProcessingJobRepository pjr;

		@Autowired
		private ImageProcessor ip;

		@Autowired
		private StorageService ss;


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
		@Transactional
		public void startAsyncProcessExecution(Long jobId) {
				ProcessingJob job = pjr.findById(jobId).orElseThrow();

				try {
						job.setStatus(JobStatus.RUNNING);
						pjr.save(job);

						ip.execute(job);

						job.setStatus(JobStatus.DONE);

				} catch (Exception e) {
						job.setStatus(JobStatus.FAILED);

				} finally {
						pjr.save(job);
				}
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
}
