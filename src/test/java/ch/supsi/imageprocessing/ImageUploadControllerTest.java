package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.service.ImageService;
import ch.supsi.imageprocessing.service.StorageService;
import ch.supsi.imageprocessing.controller.ImageUploadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; 
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageUploadController.class)
class ImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private ImageService imageService;

    @Test
    void uploadFile_ShouldReturn201CreatedWithLocationHeader_WhenSuccessful() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.png", "image/png", "data".getBytes());
        User mockUser = new User("nicola", "nicola@supsi.ch");
        Image mockImage = new Image("test.png", "/tmp/test.png", "png", mockUser);
        
        // Mocking field reflection mapping to set an ID on our entity instance
        java.lang.reflect.Field idField = Image.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(mockImage, 1L);

        when(storageService.store(any())).thenReturn(Paths.get("/tmp/test.png"));
        when(imageService.submitUpload(eq(1L), eq("test.png"), any(), eq("png"))).thenReturn(mockImage);

        mockMvc.perform(multipart("/api/upload")
                .file(mockFile)
                .param("userId", "1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/images/1"));
    }
}
