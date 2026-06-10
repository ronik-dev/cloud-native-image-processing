package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.dto.JobRequest;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.exception.UnsupportedFileFormatException;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
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

		@MockitoBean
		private UserService us; 

		private void setField(Object target, String fieldName, Object value) throws Exception {
				java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(target, value);
		}

		private User user() {
				return new User("nicola", "nicola@supsi.ch");
		}

		private Image image(Long id) throws Exception {
				Image img = new Image("test.png", "/tmp/test.png", "png", user());
				setField(img, "id", id);
				return img;
		}

		// ==========================================
		// POST /api/images  (uploadFile)
		// ==========================================

		@Test
		void uploadFile_ShouldReturn201WithLocation_WhenValid() throws Exception {
				Image saved = image(100L);
				when(us.getUserById(1L)).thenReturn(user());
				when(is.handleImageUpload(any(User.class), any())).thenReturn(saved);

				MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "bytes".getBytes());

				mockMvc.perform(multipart("/api/images").file(file).param("userId", "1"))
						.andExpect(status().isCreated())
						.andExpect(header().string("Location", endsWith("/api/images/100")));

				verify(is, times(1)).handleImageUpload(any(User.class), any());
		}

		@Test
		void uploadFile_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
				when(us.getUserById(2L)).thenThrow(new ResourceNotFoundException("User not found with ID: 2"));

				MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "bytes".getBytes());

				mockMvc.perform(multipart("/api/images").file(file).param("userId", "2"))
						.andExpect(status().isNotFound());

				verify(is, never()).handleImageUpload(any(), any());
		}

		@Test
		void uploadFile_ShouldReturn415_WhenFormatIsUnsupported() throws Exception {
				when(us.getUserById(1L)).thenReturn(user());
				when(is.handleImageUpload(any(User.class), any()))
						.thenThrow(new UnsupportedFileFormatException("Invalid file type."));

				MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "nope".getBytes());

				mockMvc.perform(multipart("/api/images").file(file).param("userId", "1"))
						.andExpect(status().isUnsupportedMediaType());
		}

		// ==========================================
		// GET /api/images/{id}  (getImage)
		// ==========================================

		@Test
		void getImage_ShouldReturn200WithBody_WhenFound() throws Exception {
				when(is.getImageData(1L)).thenReturn(image(1L));

				mockMvc.perform(get("/api/images/{id}", 1L))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.id").value(1))
						.andExpect(jsonPath("$.filename").value("test.png"))
						.andExpect(jsonPath("$.format").value("png"));
		}

		@Test
		void getImage_ShouldReturn404_WhenNotFound() throws Exception {
				when(is.getImageData(9L)).thenThrow(new ResourceNotFoundException("Image not found with ID: 9"));

				mockMvc.perform(get("/api/images/{id}", 9L))
						.andExpect(status().isNotFound());
		}

		@Test
		void getImage_ShouldReturn400_WhenIdIsNegative() throws Exception {
				mockMvc.perform(get("/api/images/-1"))
						.andExpect(status().isBadRequest());
				verifyNoInteractions(is);
		}

		// ==========================================
		// DELETE /api/images/{id}  (deleteImage)
		// ==========================================

		@Test
		void deleteImage_ShouldReturn204_WhenSuccessful() throws Exception {
				doNothing().when(is).deleteImage(1L);

				mockMvc.perform(delete("/api/images/{id}", 1L))
						.andExpect(status().isNoContent());

				verify(is, times(1)).deleteImage(1L);
		}

		@Test
		void deleteImage_ShouldReturn404_WhenNotFound() throws Exception {
				doThrow(new ResourceNotFoundException("Image not found with ID: 9")).when(is).deleteImage(9L);

				mockMvc.perform(delete("/api/images/{id}", 9L))
						.andExpect(status().isNotFound());
		}

		// ==========================================
		// POST /api/images/{id}/jobs  (createJob)
		// ==========================================

		@Test
		void createJob_ShouldReturn201_WhenRequestIsValid() throws Exception {
				Long imageId = 1L;
				ProcessingJob job = new ProcessingJob(image(imageId), JobType.FORMAT_CONVERSION, "output_file.png", "png");
				setField(job, "id", 100L);
				when(is.createJob(eq(imageId), any(JobRequest.class))).thenReturn(job);

				String payload = """
				{"type":"FORMAT_CONVERSION","outputName":"output_file","targetFormat":"png"}
				""";

				mockMvc.perform(post("/api/images/{id}/jobs", imageId)
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.id").value(100))
						.andExpect(jsonPath("$.outputName").value("output_file.png"));
		}

		@Test
		void createJob_ShouldReturn400_WhenImageIdIsNegative() throws Exception {
				String payload = """
				{"type":"FORMAT_CONVERSION","outputName":"output","targetFormat":"png"}
				""";

				mockMvc.perform(post("/api/images/-1/jobs")
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isBadRequest());

				verifyNoInteractions(is);
		}

		@Test
		void createJob_ShouldReturn400_WhenOutputNameIsBlank() throws Exception {
				// @NotEmpty on JobRequest.outputName -> MethodArgumentNotValidException -> 400
				String payload = """
				{"type":"FORMAT_CONVERSION","outputName":"","targetFormat":"png"}
				""";

				mockMvc.perform(post("/api/images/{id}/jobs", 1L)
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isBadRequest());

				verify(is, never()).createJob(any(), any());
		}

		@Test
		void createJob_ShouldReturn404_WhenImageDoesNotExist() throws Exception {
				when(is.createJob(eq(9L), any(JobRequest.class)))
						.thenThrow(new ResourceNotFoundException("Image not found with ID: 9"));

				String payload = """
				{"type":"FORMAT_CONVERSION","outputName":"out","targetFormat":"png"}
				""";

				mockMvc.perform(post("/api/images/{id}/jobs", 9L)
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isNotFound());
		}

		// ==========================================
		// GET /api/images/{id}/jobs  (getJobsByImage)
		// ==========================================

		@Test
		void getJobsByImage_ShouldReturn200AndJobList() throws Exception {
				Long imageId = 1L;
				ProcessingJob job = new ProcessingJob(image(imageId), JobType.BACKGROUND_REMOVAL, "no_bg.png", "png");
				setField(job, "id", 200L);
				when(is.getJobsByImage(imageId)).thenReturn(List.of(job));

				mockMvc.perform(get("/api/images/{id}/jobs", imageId))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$[0].id").value(200))
						.andExpect(jsonPath("$[0].type").value("BACKGROUND_REMOVAL"));
		}

		@Test
		void getJobsByImage_ShouldReturn404_WhenImageDoesNotExist() throws Exception {
				when(is.getJobsByImage(9L)).thenThrow(new ResourceNotFoundException("Image not found with ID: 9"));

				mockMvc.perform(get("/api/images/{id}/jobs", 9L))
						.andExpect(status().isNotFound());
		}
}
