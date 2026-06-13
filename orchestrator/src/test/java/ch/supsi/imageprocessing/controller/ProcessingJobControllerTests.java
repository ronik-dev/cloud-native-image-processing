package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.common.enums.JobType;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.service.ProcessingJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProcessingJobController.class)
class ProcessingJobControllerTest {

		@Autowired
		private MockMvc mockMvc;

		@MockitoBean
		private ProcessingJobService pjs;

		private void setField(Object target, String fieldName, Object value) throws Exception {
				java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(target, value);
		}

		private ProcessingJob job(Long id, String outputName) throws Exception {
				User user = new User("nicola", "nicola@supsi.ch");
				Image image = new Image("test.png", "/tmp/test.png", "png", user);
				setField(image, "id", 1L);
				ProcessingJob job = new ProcessingJob(image, JobType.FORMAT_CONVERSION, outputName, "png");
				setField(job, "id", id);
				return job;
		}

		// ==========================================
		// POST /jobs/{id}/process  (triggerProcessing)
		// ==========================================

		@Test
		void triggerProcessing_ShouldReturn202AndStartAsync_WhenJobExists() throws Exception {
				ProcessingJob job = job(100L, "output.png");
				when(pjs.processJob(100L)).thenReturn(job);

				mockMvc.perform(post("/jobs/{id}/process", 100L))
						.andExpect(status().isAccepted())
						.andExpect(jsonPath("$.id").value(100))
						.andExpect(jsonPath("$.status").value("PENDING"));

				verify(pjs, times(1)).processJob(100L);
				verify(pjs, times(1)).startAsyncProcessExecution(100L);
		}

		@Test
		void triggerProcessing_ShouldReturn404_WhenJobDoesNotExist() throws Exception {
				when(pjs.processJob(9L)).thenThrow(new ResourceNotFoundException("Job not found: 9"));

				mockMvc.perform(post("/jobs/{id}/process", 9L))
						.andExpect(status().isNotFound());

				verify(pjs, never()).startAsyncProcessExecution(any());
		}

		@Test
		void triggerProcessing_ShouldReturn400_WhenIdIsNegative() throws Exception {
				mockMvc.perform(post("/jobs/-1/process"))
						.andExpect(status().isBadRequest());
				verifyNoInteractions(pjs);
		}

		// ==========================================
		// GET /jobs/{id}  (getJobStatus)
		// ==========================================

		@Test
		void getJobStatus_ShouldReturn200WithBody_WhenFound() throws Exception {
				when(pjs.getJobStatus(100L)).thenReturn(job(100L, "output.png"));

				mockMvc.perform(get("/jobs/{id}", 100L))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.id").value(100))
						.andExpect(jsonPath("$.outputName").value("output.png"));
		}

		@Test
		void getJobStatus_ShouldReturn404_WhenNotFound() throws Exception {
				when(pjs.getJobStatus(9L)).thenThrow(new ResourceNotFoundException("Job not found with ID: 9"));

				mockMvc.perform(get("/jobs/{id}", 9L))
						.andExpect(status().isNotFound());
		}

		@Test
		void getJobStatus_ShouldReturn400_WhenIdIsNegative() throws Exception {
				mockMvc.perform(get("/jobs/-1"))
						.andExpect(status().isBadRequest());
				verifyNoInteractions(pjs);
		}

		// ==========================================
		// DELETE /jobs/{id}  (deleteJob)  -- note: no @Min(0) on this endpoint
		// ==========================================

		@Test
		void deleteJob_ShouldReturn204_WhenSuccessful() throws Exception {
				doNothing().when(pjs).deleteJob(1L);

				mockMvc.perform(delete("/jobs/{id}", 1L))
						.andExpect(status().isNoContent());

				verify(pjs, times(1)).deleteJob(1L);
		}

		@Test
		void deleteJob_ShouldReturn404_WhenNotFound() throws Exception {
				doThrow(new ResourceNotFoundException("Job not found with ID: 9")).when(pjs).deleteJob(9L);

				mockMvc.perform(delete("/jobs/{id}", 9L))
						.andExpect(status().isNotFound());
		}

		// ==========================================
		// GET /jobs/{id}/result  (downloadJobResult)
		// ==========================================

		@Test
		void downloadJobResult_ShouldReturnFileWithHeaders_WhenAvailable() throws Exception {
				byte[] data = "processed-bytes".getBytes();
				Resource resource = new ByteArrayResource(data);
				when(pjs.getJobResult(100L)).thenReturn(resource);
				when(pjs.getJobStatus(100L)).thenReturn(job(100L, "result.png"));

				mockMvc.perform(get("/jobs/{id}/result", 100L))
						.andExpect(status().isOk())
						.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
						.andExpect(header().string("Content-Disposition", containsString("result.png")))
						.andExpect(content().bytes(data));
		}

		@Test
		void downloadJobResult_ShouldReturn404_WhenResultNotAvailable() throws Exception {
				when(pjs.getJobResult(9L))
						.thenThrow(new ResourceNotFoundException("Processed file output is not available for Job ID: 9"));

				mockMvc.perform(get("/jobs/{id}/result", 9L))
						.andExpect(status().isNotFound());

				verify(pjs, never()).getJobStatus(any());
		}

		@Test
		void downloadJobResult_ShouldReturn400_WhenIdIsNegative() throws Exception {
				mockMvc.perform(get("/jobs/-1/result"))
						.andExpect(status().isBadRequest());
				verifyNoInteractions(pjs);
		}
}
