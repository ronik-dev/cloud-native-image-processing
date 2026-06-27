package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.common.dto.JobRequest;
import ch.supsi.imageprocessing.utils.ImageFormatValidator;
import ch.supsi.imageprocessing.common.exception.InvalidRequestException;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class ImageService {
		@Autowired
		private ImageRepository ir;

		@Autowired
		private ProcessingJobRepository pjr;

		@Autowired
		private StorageService ss; 

		@Transactional(isolation = Isolation.READ_COMMITTED)
		public Image handleImageUpload(User user, MultipartFile file) {
				if (file == null || file.isEmpty()) {
						throw new InvalidRequestException("Upload payload contains no file data.");
				}

				String realFormat = ImageFormatValidator.validateAndExtractFormat(file);

				String storageKey = UUID.randomUUID().toString();
				String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : storageKey;

				Image image = new Image(originalFilename, storageKey, realFormat, user);

				image = ir.saveAndFlush(image);

				ss.storeMultipartFile(file, storageKey);
				return image;
		}

		@Transactional
		public ProcessingJob createJob(Long imageId, JobRequest request) {
				if (request.outputName() == null || request.outputName().strip().isEmpty())
						throw new InvalidRequestException("Invalid or missing output name.");

				Image image = ir.findById(imageId)
						.orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));

				String fullOutputName = request.outputName()+ "." + request.targetFormat().toLowerCase().strip();
				return pjr.save(new ProcessingJob(image, request.type(), fullOutputName, request.getOrDefaultTargetFormat()));
		}


		@Transactional(readOnly = true)
		public Image getImageData(Long imageId) {
				return ir.findById(imageId)
						.orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId));
		}

		@Transactional(readOnly = true)
		public List<Image> getImagesByUser(User user){
				return ir.findByUserId(user.getId());
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
				if (!ir.existsById(imageId)) {
						throw new ResourceNotFoundException("Image not found with ID: " + imageId);
				}
				Image i = ir.findById(imageId).orElseThrow(()->new ResourceNotFoundException("Image not found with ID: " + imageId));
				for (ProcessingJob j : pjr.findByImageId(i.getId())){
						if(j.getStatus()==JobStatus.DONE) ss.deleteResource(j.getTargetStorageKey());
				}
				ir.delete(i);
				ss.deleteResource(i.getStorageKey());
		}

		@Transactional
		public void deleteAllByUser(Long userId) {
				for (Image image : ir.findByUserId(userId)) {
						for (ProcessingJob job : pjr.findByImageId(image.getId())) {
								if (job.getStatus() == JobStatus.DONE && job.getTargetStorageKey() != null) {
										ss.deleteResource(job.getTargetStorageKey());
								}
						}
						ss.deleteResource(image.getStorageKey());
				}
		}
}
