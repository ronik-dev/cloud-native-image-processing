package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

@Service
public class ImageService {
		@Autowired
		private ImageRepository ir;
		@Autowired
		private ProcessingJobRepository pjr;

		@Autowired
		private UserRepository ur;

		@Transactional(isolation = Isolation.READ_COMMITTED)
		public ProcessingJob createConversionJob(Long imageId, String outputName, String targetFormat) {

				if (targetFormat == null || targetFormat.strip().isEmpty()) 
						throw new InvalidRequestException("Invalid or missing target format.");
				if (outputName == null || outputName.strip().isEmpty()) 
						throw new InvalidRequestException("Invalid or missing output name.");

				Image image = ir.findById(imageId)
						.orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));

				String fullOutputName = outputName + "." + targetFormat.toLowerCase().strip();
				ProcessingJob job = new ProcessingJob(image, JobType.FORMAT_CONVERSION, fullOutputName, targetFormat);

				ProcessingJob savedJob = pjr.save(job);
				return savedJob;
		}

		@Transactional(isolation = Isolation.READ_COMMITTED)
		public Image submitUpload(Long userId, String filename, String storagePath, String format) {
				if (filename == null || filename.strip().isEmpty()) {
						throw new InvalidRequestException("Filename cannot be empty.");
				}

				User user = ur.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found:"));

				Image image = new Image(filename, storagePath, format, user);
				Image savedImage = ir.save(image);

				return savedImage;
		}

		@Transactional(readOnly = true)
		public Image getImageData(Long imageId) {
				return ir.findById(imageId)
						.orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));
		}

		@Transactional(readOnly = true)
		public ProcessingJob getJobStatus(Long jobId) {
				return pjr.findById(jobId)
						.orElseThrow(() -> new ResourceNotFoundException("Job not found with ID: " + jobId));
		}

		@Transactional(readOnly = true)
		public List<ProcessingJob> getJobsByImage(Long imageId) {
				if (!ir.existsById(imageId)) {
						throw new ResourceNotFoundException("Image not found with ID: " + imageId);
				}
				return pjr.findByImageId(imageId);
		}

		@Transactional(readOnly = true)
		public List<Image> getAllImages() {
				return ir.findAll();
		}

		@Transactional
		public void deleteImage(Long imageId){
				Optional<Image> io = ir.findById(imageId);
				Image i = io.orElseThrow(()->new ResourceNotFoundException("Image not found with ID: " + imageId));
				ir.delete(i);
		}
}
