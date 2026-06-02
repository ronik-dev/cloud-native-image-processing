package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.service.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

		@Mock
		private UserRepository userRepository;

		@InjectMocks
		private ImageService imageService;

		@Test
		void createConversionJob_ShouldSucceed_WhenInputsAreValid() throws Exception {
				// Arrange
				Long imageId = 1L;
				String outputName = "outputName";
				String targetFormat = "png";

				User mockUser = new User("john_doe", "john@example.com");
				Image mockImage = new Image("imageName", "/tmp/inputs/img.jpg", "jpg", mockUser);

				when(imageRepository.findById(imageId)).thenReturn(Optional.of(mockImage));

				// Capture job save and simulate DB primary key identity assignment
				when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
						ProcessingJob jobToSave = invocation.getArgument(0);
						java.lang.reflect.Field idField = ProcessingJob.class.getDeclaredField("id");
						idField.setAccessible(true);
						idField.set(jobToSave, 100L); 
						return jobToSave;
				});

				// Act 
				ProcessingJob result = imageService.createConversionJob(imageId, outputName, targetFormat);

				// Assert
				assertNotNull(result);
				assertEquals(100L, result.getId());
				assertEquals("outputName.png", result.getOutputName());
				assertEquals(targetFormat, result.getTargetFormat());

				verify(imageRepository, times(1)).findById(imageId);
				verify(processingJobRepository, times(1)).save(any(ProcessingJob.class));
		}

		@Test
		void createConversionJob_ShouldThrow_WhenTargetFormatIsEmpty() {
				assertThrows(ch.supsi.imageprocessing.exception.InvalidRequestException.class, () -> {
						imageService.createConversionJob(1L, "output", "    ");
				});

				verifyNoInteractions(imageRepository);
				verifyNoInteractions(processingJobRepository);
		}

		@Test
		void createConversionJob_ShouldThrow_WhenOutputNameIsEmpty() {
				assertThrows(ch.supsi.imageprocessing.exception.InvalidRequestException.class, () -> {
						imageService.createConversionJob(1L, "", "png");
				});

				verifyNoInteractions(imageRepository);
				verifyNoInteractions(processingJobRepository);
		}

		@Test
		void createConversionJob_ShouldThrow_WhenImageDoesNotExist() {
				Long nonExistentImageId = 999L;
				when(imageRepository.findById(nonExistentImageId)).thenReturn(Optional.empty());

				assertThrows(ch.supsi.imageprocessing.exception.ResourceNotFoundException.class, () -> {
						imageService.createConversionJob(nonExistentImageId, "output", "png");
				});

				verify(imageRepository, times(1)).findById(nonExistentImageId);
				verifyNoInteractions(processingJobRepository);
		}
}
