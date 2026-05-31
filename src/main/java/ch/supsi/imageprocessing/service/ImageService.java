package ch.supsi.imageprocessing.service;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
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

		@Transactional(isolation = Isolation.READ_COMMITTED)
		public ProcessingJob createConversionJob(Long imageId, String outputName, String targetFormat) {

				if(targetFormat == null || targetFormat.strip().isEmpty()) 
						throw new InvalidRequestException("Invalid or missing target format.");
				if(outputName == null || outputName.strip().isEmpty()) 
						throw new InvalidRequestException("Invalid or missing output name.");
				Image image = ir.findById(imageId)
						.orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));

				ProcessingJob job = new ProcessingJob(image, JobType.FORMAT_CONVERSION, outputName+"."+targetFormat, targetFormat);

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
