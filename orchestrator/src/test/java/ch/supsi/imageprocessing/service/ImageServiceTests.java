package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.common.dto.JobRequest;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.common.enums.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.exception.InvalidRequestException;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.common.exception.UnsupportedFileFormatException;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

		@Mock
		private ImageRepository ir;

		@Mock
		private ProcessingJobRepository pjr;

		@Mock
		private StorageService ss;

		@InjectMocks
		private ImageService imageService;

		// Real PNG file signature so the static ImageFormatValidator detects "image/png".
		private static final byte[] PNG_BYTES = new byte[]{
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
						0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
		};
		private static final byte[] NON_IMAGE_BYTES = "this is definitely not an image".getBytes();

		private User newUser() {
				return new User("john_doe", "john@example.com");
		}

		// ==========================================
		// handleImageUpload
		// ==========================================

		@Test
		void handleImageUpload_ShouldPersistImageAndStoreFile_WhenPayloadIsValidPng() {
				User user = newUser();
				MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_BYTES);

				when(ir.saveAndFlush(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

				Image result = imageService.handleImageUpload(user, file);

				assertNotNull(result);
				assertEquals("photo.png", result.getName());
				assertEquals("png", result.getFormat());
				assertEquals(user, result.getUser());
				assertNotNull(result.getStorageKey());

				verify(ir, times(1)).saveAndFlush(any(Image.class));
				// The file must be written to storage under the same generated key as the entity.
				verify(ss, times(1)).storeMultipartFile(eq(file), eq(result.getStorageKey()));
		}

		@Test
		void handleImageUpload_ShouldFallBackToStorageKeyAsName_WhenOriginalFilenameIsNull() throws Exception {
				User user = newUser();
				MultipartFile file = mock(MultipartFile.class);
				when(file.isEmpty()).thenReturn(false);
				when(file.getOriginalFilename()).thenReturn(null);
				// Fresh stream per call so both the validator and any later read succeed.
				when(file.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(PNG_BYTES));

				when(ir.saveAndFlush(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

				Image result = imageService.handleImageUpload(user, file);

				assertNotNull(result);
				// With a null original filename, the name falls back to the generated storage key.
				assertEquals(result.getStorageKey(), result.getName());
				verify(ss, times(1)).storeMultipartFile(eq(file), anyString());
		}

		@Test
		void handleImageUpload_ShouldThrowInvalidRequest_WhenFileIsNull() {
				InvalidRequestException ex = assertThrows(InvalidRequestException.class,
								() -> imageService.handleImageUpload(newUser(), null));
				assertEquals("Upload payload contains no file data.", ex.getMessage());
				verifyNoInteractions(ir, ss);
		}

		@Test
		void handleImageUpload_ShouldThrowInvalidRequest_WhenFileIsEmpty() {
				MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

				assertThrows(InvalidRequestException.class,
								() -> imageService.handleImageUpload(newUser(), emptyFile));
				verifyNoInteractions(ir, ss);
		}

		@Test
		void handleImageUpload_ShouldThrowUnsupportedFileFormat_WhenContentIsNotAnImage() {
				MockMultipartFile badFile = new MockMultipartFile("file", "notes.txt", "text/plain", NON_IMAGE_BYTES);

				assertThrows(UnsupportedFileFormatException.class,
								() -> imageService.handleImageUpload(newUser(), badFile));
				verifyNoInteractions(ir, ss);
		}

		// ==========================================
		// createJob
		// ==========================================

		@Test
		void createJob_ShouldSucceed_WhenInputsAreValid() throws Exception {
				Long imageId = 1L;
				Image mockImage = new Image("imageName", "/tmp/inputs/img.jpg", "jpg", newUser());
				when(ir.findById(imageId)).thenReturn(Optional.of(mockImage));
				when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> {
						ProcessingJob job = inv.getArgument(0);
						java.lang.reflect.Field idField = ProcessingJob.class.getDeclaredField("id");
						idField.setAccessible(true);
						idField.set(job, 100L);
						return job;
				});

				ProcessingJob result = imageService.createJob(imageId,
								new JobRequest(JobType.FORMAT_CONVERSION, "outputName", "png"));

				assertNotNull(result);
				assertEquals(100L, result.getId());
				assertEquals("outputName.png", result.getOutputName());
				assertEquals("png", result.getTargetFormat());
				verify(ir, times(1)).findById(imageId);
				verify(pjr, times(1)).save(any(ProcessingJob.class));
		}

		@Test
		void createJob_ShouldDefaultTargetFormatToPng_WhenTargetFormatIsBlank() {
				Long imageId = 1L;
				Image mockImage = new Image("imageName", "/tmp/inputs/img.jpg", "jpg", newUser());
				when(ir.findById(imageId)).thenReturn(Optional.of(mockImage));
				when(pjr.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

				// Note: outputName concatenation uses targetFormat() directly, so a blank (but
				// non-null) value is fine here; getOrDefaultTargetFormat() supplies "png".
				ProcessingJob result = imageService.createJob(imageId,
								new JobRequest(JobType.FORMAT_CONVERSION, "out", " "));

				assertEquals("png", result.getTargetFormat());
		}

		@Test
		void createJob_ShouldThrowInvalidRequest_WhenOutputNameIsNull() {
				JobRequest request = new JobRequest(JobType.FORMAT_CONVERSION, null, "png");

				assertThrows(InvalidRequestException.class, () -> imageService.createJob(1L, request));
				verifyNoInteractions(ir, pjr);
		}

		@Test
		void createJob_ShouldThrowInvalidRequest_WhenOutputNameIsBlank() {
				JobRequest request = new JobRequest(JobType.FORMAT_CONVERSION, "   ", "png");

				assertThrows(InvalidRequestException.class, () -> imageService.createJob(1L, request));
				verifyNoInteractions(ir, pjr);
		}

		@Test
		void createJob_ShouldThrow_WhenImageDoesNotExist() {
				Long nonExistentImageId = 999L;
				when(ir.findById(nonExistentImageId)).thenReturn(Optional.empty());

				assertThrows(ResourceNotFoundException.class,
								() -> imageService.createJob(nonExistentImageId,
															 new JobRequest(JobType.FORMAT_CONVERSION, "output", "png")));

				verify(ir, times(1)).findById(nonExistentImageId);
				verifyNoInteractions(pjr);
		}

		// ==========================================
		// getImageData
		// ==========================================

		@Test
		void getImageData_ShouldReturnImage_WhenItExists() {
				Image mockImage = new Image("img", "key", "png", newUser());
				when(ir.findById(1L)).thenReturn(Optional.of(mockImage));

				assertSame(mockImage, imageService.getImageData(1L));
		}

		@Test
		void getImageData_ShouldThrow_WhenImageDoesNotExist() {
				when(ir.findById(7L)).thenReturn(Optional.empty());
				assertThrows(ResourceNotFoundException.class, () -> imageService.getImageData(7L));
		}

		// ==========================================
		// getImagesByUser
		// ==========================================

		@Test
		void getImagesByUser_ShouldDelegateToRepositoryByUserId() {
				User user = newUser();
				// user id is null here, but getImagesByUser passes user.getId() through; stub on that value.
				List<Image> expected = List.of(new Image("a", "k1", "png", user));
				when(ir.findByUserId(user.getId())).thenReturn(expected);

				assertEquals(expected, imageService.getImagesByUser(user));
				verify(ir).findByUserId(user.getId());
		}

		// ==========================================
		// getJobsByImage
		// ==========================================

		@Test
		void getJobsByImage_ShouldReturnJobs_WhenImageExists() {
				Long imageId = 5L;
				Image img = new Image("a", "k", "png", newUser());
				List<ProcessingJob> jobs = List.of(new ProcessingJob(img, JobType.FORMAT_CONVERSION, "o.png", "png"));
				when(ir.existsById(imageId)).thenReturn(true);
				when(pjr.findByImageId(imageId)).thenReturn(jobs);

				assertEquals(jobs, imageService.getJobsByImage(imageId));
		}

		@Test
		void getJobsByImage_ShouldThrow_WhenImageDoesNotExist() {
				when(ir.existsById(5L)).thenReturn(false);
				assertThrows(ResourceNotFoundException.class, () -> imageService.getJobsByImage(5L));
				verify(pjr, never()).findByImageId(any());
		}

		// ==========================================
		// getAllImages
		// ==========================================

		@Test
		void getAllImages_ShouldDelegateToRepository() {
				List<Image> all = List.of(new Image("a", "k", "png", newUser()));
				when(ir.findAll()).thenReturn(all);
				assertEquals(all, imageService.getAllImages());
		}

		// ==========================================
		// deleteImage
		// ==========================================

		@Test
		void deleteImage_ShouldDeleteEntityAndDoneJobResources() {
				Long imageId = 3L;
				Image img = new Image("a", "image-key", "png", newUser());

				ProcessingJob doneJob = new ProcessingJob(img, JobType.FORMAT_CONVERSION, "o.png", "png");
				doneJob.setStatus(JobStatus.DONE);
				doneJob.setTargetStorageKey("done-output-key");

				ProcessingJob pendingJob = new ProcessingJob(img, JobType.FORMAT_CONVERSION, "p.png", "png");
				// stays PENDING -> its resource must not be deleted

				when(ir.existsById(imageId)).thenReturn(true);
				when(ir.findById(imageId)).thenReturn(Optional.of(img));
				when(pjr.findByImageId(any())).thenReturn(List.of(doneJob, pendingJob));

				imageService.deleteImage(imageId);

				verify(ss, times(1)).deleteResource("done-output-key");
				verify(ss, times(1)).deleteResource("image-key");
				verify(ss, never()).deleteResource(isNull());
				verify(ir, times(1)).delete(img);
		}

		@Test
		void deleteImage_ShouldThrow_WhenImageDoesNotExist() {
				when(ir.existsById(3L)).thenReturn(false);
				assertThrows(ResourceNotFoundException.class, () -> imageService.deleteImage(3L));
				verify(ir, never()).delete(any());
				verifyNoInteractions(ss);
		}

		// ==========================================
		// deleteAllByUser
		// ==========================================

		@Test
		void deleteAllByUser_ShouldDeleteImageAndDoneJobResourcesForEachImage() {
				Long userId = 42L;
				Image img1 = new Image("a", "img1-key", "png", newUser());
				Image img2 = new Image("b", "img2-key", "png", newUser());

				ProcessingJob doneJob = new ProcessingJob(img1, JobType.FORMAT_CONVERSION, "o.png", "png");
				doneJob.setStatus(JobStatus.DONE);
				doneJob.setTargetStorageKey("job-key");

				ProcessingJob failedJob = new ProcessingJob(img2, JobType.FORMAT_CONVERSION, "f.png", "png");
				failedJob.setStatus(JobStatus.FAILED);

				when(ir.findByUserId(userId)).thenReturn(List.of(img1, img2));
				when(pjr.findByImageId(any())).thenReturn(List.of(doneJob), List.of(failedJob));

				imageService.deleteAllByUser(userId);

				verify(ss).deleteResource("job-key");
				verify(ss).deleteResource("img1-key");
				verify(ss).deleteResource("img2-key");
				// failed job has no/blank output key -> never deleted
				verify(ss, never()).deleteResource(isNull());
		}
}
