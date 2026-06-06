package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.util.List;

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

    @Test
    void getImagesByUser_ShouldReturnFilteredImages_WhenInvoked() throws Exception {
        // Arrange
        User targetUser = new User("nicola", "nicola@supsi.ch");
        setField(targetUser, "id", 1L);

        User otherUser = new User("other", "other@supsi.ch");
        setField(otherUser, "id", 2L);

        Image targetImage = new Image("my_image.png", "/tmp/img1.png", "png", targetUser);
        setField(targetImage, "id", 100L);

        Image otherImage = new Image("other_image.png", "/tmp/img2.png", "png", otherUser);
        setField(otherImage, "id", 101L);

        // Return a mixed list from the service
        when(is.getAllImages()).thenReturn(List.of(targetImage, otherImage));

        // Act & Assert: The controller should filter out 'otherImage'
        mockMvc.perform(get("/api/users/1/images")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].filename").value("my_image.png")); // Or 'name', depending on your ImageResponse DTO
    }

    @Test
    void getImagesByUser_ShouldReturn400_WhenUserIdIsNegative() throws Exception {
        // The @Min(0) validation should block this before the controller executes
        mockMvc.perform(get("/api/users/-5/images")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        
        verifyNoInteractions(is);
    }

    @Test
    void deleteUser_ShouldReturn204_WhenSuccessful() throws Exception {
        Long userId = 1L;
        doNothing().when(us).deleteUser(userId);

        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().isNoContent());

        verify(us, times(1)).deleteUser(userId);
    }

    // Helper method to set IDs via reflection for testing
    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
