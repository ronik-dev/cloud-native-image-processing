package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

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

		private User user(Long id) throws Exception {
				User u = new User("nicola", "nicola@supsi.ch");
				setField(u, "id", id);
				return u;
		}

		// ==========================================
		// POST /api/users  (newUser)
		// ==========================================

		@Test
		void newUser_ShouldReturn201WithBody_WhenValid() throws Exception {
				when(us.createUser("nicola", "nicola@supsi.ch")).thenReturn(user(1L));

				String payload = """
				{"username":"nicola","email":"nicola@supsi.ch"}
				""";

				mockMvc.perform(post("/api/users")
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.id").value(1))
						.andExpect(jsonPath("$.username").value("nicola"))
						.andExpect(jsonPath("$.email").value("nicola@supsi.ch"));
		}

		@Test
		void newUser_ShouldReturn400_WhenUsernameIsBlank() throws Exception {
				// @NotBlank on UserRequest.username -> MethodArgumentNotValidException -> 400
				String payload = """
				{"username":"","email":"nicola@supsi.ch"}
				""";

				mockMvc.perform(post("/api/users")
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isBadRequest());

				verify(us, never()).createUser(anyString(), anyString());
		}

		@Test
		void newUser_ShouldReturn400_WhenEmailIsInvalid() throws Exception {
				// @Email on UserRequest.email -> MethodArgumentNotValidException -> 400
				String payload = """
				{"username":"nicola","email":"not-an-email"}
				""";

				mockMvc.perform(post("/api/users")
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isBadRequest());

				verify(us, never()).createUser(anyString(), anyString());
		}

		@Test
		void newUser_ShouldReturn409_WhenUsernameOrEmailAlreadyExists() throws Exception {
				when(us.createUser(anyString(), anyString()))
						.thenThrow(new DataIntegrityViolationException("duplicate key"));

				String payload = """
				{"username":"nicola","email":"nicola@supsi.ch"}
				""";

				mockMvc.perform(post("/api/users")
								.contentType(MediaType.APPLICATION_JSON)
								.content(payload))
						.andExpect(status().isConflict());
		}

		// ==========================================
		// GET /api/users  (getAllUsers)
		// ==========================================

		@Test
		void getAllUsers_ShouldReturn200AndList() throws Exception {
				when(us.getAllUsers()).thenReturn(List.of(user(1L), user(2L)));

				mockMvc.perform(get("/api/users"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.length()").value(2))
						.andExpect(jsonPath("$[0].id").value(1))
						.andExpect(jsonPath("$[1].id").value(2));
		}

		// ==========================================
		// GET /api/users/{id}/images  (getImagesByUser)
		// ==========================================

		@Test
		void getImagesByUser_ShouldReturnFilteredImages_WhenInvoked() throws Exception {
				User targetUser = user(1L);
				Image targetImage = new Image("my_image.png", "/tmp/img1.png", "png", targetUser);
				setField(targetImage, "id", 100L);

				when(us.getUserById(1L)).thenReturn(targetUser);
				when(is.getImagesByUser(targetUser)).thenReturn(List.of(targetImage));

				mockMvc.perform(get("/api/users/{id}/images", 1L))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.length()").value(1))
						.andExpect(jsonPath("$[0].id").value(100))
						.andExpect(jsonPath("$[0].filename").value("my_image.png"))
						.andExpect(jsonPath("$[0].userId").value(1));
		}

		@Test
		void getImagesByUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
				when(us.getUserById(9L)).thenThrow(new ResourceNotFoundException("User not found with ID: 9"));

				mockMvc.perform(get("/api/users/{id}/images", 9L))
						.andExpect(status().isNotFound());

				verify(is, never()).getImagesByUser(any());
		}

		@Test
		void getImagesByUser_ShouldReturn400_WhenUserIdIsNegative() throws Exception {
				mockMvc.perform(get("/api/users/-5/images"))
						.andExpect(status().isBadRequest());

				verifyNoInteractions(is);
		}

		// ==========================================
		// DELETE /api/users/{id}  (deleteUser)
		// ==========================================

		@Test
		void deleteUser_ShouldReturn204_WhenSuccessful() throws Exception {
				doNothing().when(us).deleteUser(1L);

				mockMvc.perform(delete("/api/users/{id}", 1L))
						.andExpect(status().isNoContent());

				verify(us, times(1)).deleteUser(1L);
		}

		@Test
		void deleteUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
				doThrow(new ResourceNotFoundException("User not found with ID: 9")).when(us).deleteUser(9L);

				mockMvc.perform(delete("/api/users/{id}", 9L))
						.andExpect(status().isNotFound());
		}

		@Test
		void deleteUser_ShouldReturn400_WhenIdIsNegative() throws Exception {
				mockMvc.perform(delete("/api/users/-1"))
						.andExpect(status().isBadRequest());

				verifyNoInteractions(us);
		}
}
