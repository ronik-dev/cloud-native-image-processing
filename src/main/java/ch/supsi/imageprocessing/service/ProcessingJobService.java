package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.processor.ImageProcessor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@Service
public class ProcessingJobService {

		@Autowired
		private ProcessingJobRepository pjr;

		@Autowired
		private ImageProcessor ip;

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
				job = pjr.saveAndFlush(job);

				try {
						String storageKey = ip.execute(job);

						job.setTargetStorageKey(storageKey);
						job.setStatus(JobStatus.DONE);
				} catch (Exception e) {
						e.printStackTrace();
						job.setStatus(JobStatus.FAILED);
				}

				return pjr.save(job);
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
				pjr.delete(job);
		}
}
