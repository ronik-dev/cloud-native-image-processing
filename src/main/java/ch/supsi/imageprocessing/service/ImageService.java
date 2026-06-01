package ch.supsi.imageprocessing.service;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.processor.ImageProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ImageService {
		@Autowired
		private ImageRepository ir;
		@Autowired
		private ProcessingJobRepository pjr;
		@Autowired
		private ImageProcessor imageProcessor;

		@Transactional(isolation = Isolation.READ_COMMITTED)
		public ProcessingJob createConversionJob(Long imageId, String outputName, String targetFormat) { // Removed resultPath parameter

				if (targetFormat == null || targetFormat.strip().isEmpty()) 
						throw new InvalidRequestException("Invalid or missing target format.");
				if (outputName == null || outputName.strip().isEmpty()) 
						throw new InvalidRequestException("Invalid or missing output name.");

				Image image = ir.findById(imageId)
						.orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));

				String fullOutputName = outputName + "." + targetFormat.toLowerCase().strip();
				ProcessingJob job = new ProcessingJob(image, JobType.FORMAT_CONVERSION, fullOutputName, targetFormat);

				ProcessingJob savedJob = pjr.save(job);
				return processJob(savedJob.getId());
		}

		@Transactional
		public ProcessingJob processJob(Long jobId) {
				ProcessingJob job = pjr.findById(jobId)
						.orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

				job.setStatus(JobStatus.RUNNING);
				job = pjr.saveAndFlush(job);

				try {
						// Delegate the physical file operation to the processor component
						String resultPath = imageProcessor.execute(job);

						job.setResultPath(resultPath);
						job.setStatus(JobStatus.DONE);
				} catch (Exception e) {
						job.setStatus(JobStatus.FAILED);
				}

				return pjr.save(job);
		}

		@Transactional(readOnly = true)
		public Image getImageData(Long imageId) {
				return ir.findById(imageId)
						.orElseThrow(() -> new IllegalArgumentException("Image not found with ID: " + imageId));
		}

		@Transactional(readOnly = true)
		public ProcessingJob getJobStatus(Long jobId) {
				return pjr.findById(jobId)
						.orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));
		}
}
