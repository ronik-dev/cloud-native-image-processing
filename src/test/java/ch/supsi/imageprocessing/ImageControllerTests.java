package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.controller.ImageController;
import ch.supsi.imageprocessing.dto.JobRequest;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.service.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

		@Autowired
		private MockMvc mockMvc;

		@MockitoBean
		private ImageService is;

		@Test
		void createJob_ShouldReturn202_WhenRequestIsValid() throws Exception {
				// Arrange
				Long imageId = 1L;

				// 1. Use Java 21 Text Blocks (""") instead of ObjectMapper!
				String jsonPayload = """
				{
						"type": "FORMAT_CONVERSION",
								"outputName": "output_file",
								"targetFormat": "png"
				}
				""";

				User mockUser = new User("nicola", "nicola@supsi.ch");
				Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
				setField(mockImage, "id", imageId);

				ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.FORMAT_CONVERSION, "output_file.png", "png");
				setField(mockJob, "id", 100L);

				// Notice we use any(JobRequest.class) because Spring will parse the JSON string for us
				when(is.createJob(eq(imageId), any(JobRequest.class))).thenReturn(mockJob);

				// Act & Assert
				mockMvc.perform(post("/api/images/{id}/jobs", imageId)
								.contentType(MediaType.APPLICATION_JSON)
								.content(jsonPayload)) // 2. Pass the raw string directly
						.andExpect(status().isAccepted())
						.andExpect(jsonPath("$.id").value(100))
						.andExpect(jsonPath("$.outputName").value("output_file.png"));
		}

		@Test
		void createJob_ShouldReturn400_WhenImageIdIsNegative() throws Exception {
				// Arrange
				String jsonPayload = """
				{
						"type": "FORMAT_CONVERSION",
								"outputName": "output",
								"targetFormat": "png"
				}
				""";

				// Act & Assert (The @Min(0) constraint should block this)
				mockMvc.perform(post("/api/images/-1/jobs")
								.contentType(MediaType.APPLICATION_JSON)
								.content(jsonPayload))
						.andExpect(status().isBadRequest());

				verifyNoInteractions(is);
		}

		@Test
		void getJobsByImage_ShouldReturn200AndJobList() throws Exception {
				// Arrange
				Long imageId = 1L;
				User mockUser = new User("nicola", "nicola@supsi.ch");
				Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
				setField(mockImage, "id", imageId);

				ProcessingJob mockJob = new ProcessingJob(mockImage, JobType.BACKGROUND_REMOVAL, "no_bg.png", "png");
				setField(mockJob, "id", 200L);

				when(is.getJobsByImage(imageId)).thenReturn(List.of(mockJob));

				// Act & Assert
				mockMvc.perform(get("/api/images/{id}/jobs", imageId)
								.contentType(MediaType.APPLICATION_JSON))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$[0].id").value(200))
						.andExpect(jsonPath("$[0].type").value("BACKGROUND_REMOVAL"));
		}

		@Test
		void deleteImage_ShouldReturn204_WhenSuccessful() throws Exception {
				Long imageId = 1L;
				doNothing().when(is).deleteImage(imageId);

				mockMvc.perform(delete("/api/images/{id}", imageId))
						.andExpect(status().isNoContent());

				verify(is, times(1)).deleteImage(imageId);
		}

		// Helper method to set IDs via reflection for testing
		private void setField(Object target, String fieldName, Object value) throws Exception {
				java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(target, value);
		}
}
