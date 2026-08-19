package ch.supsi.imageprocessing.controller;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void findOrCreateUser_ShouldReturn200WithBody() throws Exception {
        when(us.findOrCreateUser("nicola", "nicola@supsi.ch")).thenReturn(user(1L));
        String payload = """
            {"username":"nicola","email":"nicola@supsi.ch"}
            """;

        mockMvc.perform(post("/users/find-or-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("nicola"))
                .andExpect(jsonPath("$.email").value("nicola@supsi.ch"));
    }

    @Test
    void getImagesByUser_ShouldReturnFilteredImages_WhenInvoked() throws Exception {
        User targetUser = user(1L);
        Image targetImage = new Image("my_image.png", "/tmp/img1.png", "png", targetUser);
        setField(targetImage, "id", 100L);

        when(us.getUserById(1L)).thenReturn(targetUser);
        when(is.getImagesByUser(targetUser)).thenReturn(List.of(targetImage));

        mockMvc.perform(get("/users/{id}/images", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].filename").value("my_image.png"));
    }

    @Test
    void getImagesByUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
        when(us.getUserById(9L)).thenThrow(new ResourceNotFoundException("User not found"));
        mockMvc.perform(get("/users/{id}/images", 9L))
                .andExpect(status().isNotFound());
    }

    //@Test
    //void deleteUser_ShouldReturn204_WhenSuccessful() throws Exception {
    //    doNothing().when(us).deleteUser(1L);
    //    mockMvc.perform(delete("/users/{id}", 1L))
    //            .andExpect(status().isNoContent());
    //    verify(us, times(1)).deleteUser(1L);
    //}

    //@Test
    //void deleteUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
    //    doThrow(new ResourceNotFoundException("User not found")).when(us).deleteUser(9L);
    //    mockMvc.perform(delete("/users/{id}", 9L))
    //            .andExpect(status().isNotFound());
    //}

    //@Test
    //void deleteUser_ShouldReturn400_WhenIdIsNegative() throws Exception {
    //    mockMvc.perform(delete("/users/-1"))
    //            .andExpect(status().isBadRequest());
    //    verifyNoInteractions(us);
    //}
}
