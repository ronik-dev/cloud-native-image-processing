package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.JobStatus;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.service.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

		@Mock
		private ImageRepository imageRepository;

		@Mock
		private ProcessingJobRepository processingJobRepository;

		@InjectMocks
		private ImageService imageService;

		// ==========================================
		// SUCCESS TESTS
		// ==========================================

		@Test
		void createConversionJob_ShouldSucceed_WhenInputsAreValid() {
				Long imageId = 1L;
				String outputName = "outputName";
				String targetFormat = "png";

				User mockUser = new User("john_doe", "john@example.com");
				Image mockImage = new Image("imageName", new byte[]{0, 1, 2}, "jpg", mockUser);

				when(imageRepository.findById(imageId)).thenReturn(Optional.of(mockImage));

				ArgumentCaptor<ProcessingJob> jobCaptor = ArgumentCaptor.forClass(ProcessingJob.class);

				when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

				ProcessingJob result = imageService.createConversionJob(imageId, outputName, targetFormat);

				assertNotNull(result);
				assertEquals(mockImage, result.getImage());
				assertEquals(JobType.FORMAT_CONVERSION, result.getType());
				assertEquals("outputName.png", result.getOutputName());
				assertEquals("png", result.getTargetFormat());
				assertEquals(JobStatus.PENDING, result.getStatus());

				verify(imageRepository, times(1)).findById(imageId);
				verify(processingJobRepository, times(1)).save(jobCaptor.capture());
		}

		// ==========================================
		// FAIL-FAST VALIDATION TESTS
		// ==========================================

		@Test
		void createConversionJob_ShouldThrow_WhenTargetFormatIsEmpty() {
				// Change expected class to InvalidRequestException
				ch.supsi.imageprocessing.exception.InvalidRequestException exception = 
						assertThrows(ch.supsi.imageprocessing.exception.InvalidRequestException.class, () -> {
								imageService.createConversionJob(1L, "output", "    ");
						});

				assertEquals("Invalid or missing target format.", exception.getMessage());

				verifyNoInteractions(imageRepository);
				verifyNoInteractions(processingJobRepository);
		}

		@Test
		void createConversionJob_ShouldThrow_WhenOutputNameIsEmpty() {
				// Change expected class to InvalidRequestException
				ch.supsi.imageprocessing.exception.InvalidRequestException exception = 
						assertThrows(ch.supsi.imageprocessing.exception.InvalidRequestException.class, () -> {
								imageService.createConversionJob(1L, "", "png");
						});

				assertEquals("Invalid or missing output name.", exception.getMessage());

				verifyNoInteractions(imageRepository);
				verifyNoInteractions(processingJobRepository);
		}

		// ==========================================
		// DATABASE VALIDATION TESTS
		// ==========================================

		@Test
		void createConversionJob_ShouldThrow_WhenImageDoesNotExist() {
				Long nonExistentImageId = 999L;
				when(imageRepository.findById(nonExistentImageId)).thenReturn(Optional.empty());

				// Change expected class to ResourceNotFoundException
				ch.supsi.imageprocessing.exception.ResourceNotFoundException exception = 
						assertThrows(ch.supsi.imageprocessing.exception.ResourceNotFoundException.class, () -> {
								imageService.createConversionJob(nonExistentImageId, "output", "png");
						});

				assertEquals("Image not found with ID: 999", exception.getMessage());

				verify(imageRepository, times(1)).findById(nonExistentImageId);
				verifyNoInteractions(processingJobRepository);
		}
}
