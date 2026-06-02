package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.controller.ImageController;
import ch.supsi.imageprocessing.repository.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; 
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

		@Autowired
		private MockMvc mockMvc;

		@MockitoBean
		private ImageRepository imageRepository;

		@Test
		void getAllImages_ShouldReturnFlatJsonArray_WhenInvoked() throws Exception {
				User mockUser = new User("test001", "test001@e.mail");

				java.lang.reflect.Field userIdField = User.class.getDeclaredField("id");
				userIdField.setAccessible(true);
				userIdField.set(mockUser, 1L); 
				Image mockImage = new Image("test_image.png", "/tmp/path/test_image.png", "png", mockUser);
				java.lang.reflect.Field idField = Image.class.getDeclaredField("id");
				idField.setAccessible(true);
				idField.set(mockImage, 1L);

				when(imageRepository.findAll()).thenReturn(List.of(mockImage));

				mockMvc.perform(get("/api/images/list")
								.contentType(MediaType.APPLICATION_JSON))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$[0].id").value(1))
						.andExpect(jsonPath("$[0].filename").value("test_image.png"))
						.andExpect(jsonPath("$[0].userId").value(1)) // This will now pass successfully!
						.andExpect(jsonPath("$[0].user").doesNotExist());
		}
}
