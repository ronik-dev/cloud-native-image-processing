package ch.supsi.imageprocessing;


import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

    private StorageService storageService;

    @TempDir
    Path sharedTempDir; // JUnit 5 automatically creates and cleans up a safe sandbox directory

    @BeforeEach
    void setUp() {
        storageService = new StorageService();
        // Dynamically inject the temporary directory path into the private property string field
        ReflectionTestUtils.setField(storageService, "uploadDirStr", sharedTempDir.toString());
    }

    @Test
    void store_ShouldSaveFileToDisk_WhenPayloadIsValid() {
        MockMultipartFile validFile = new MockMultipartFile(
            "file", 
            "test_image.png", 
            "image/png", 
            "fake-binary-data".getBytes()
        );

        Path savedPath = storageService.store(validFile);

        assertThat(savedPath).isNotNull();
        assertThat(Files.exists(savedPath)).isTrue();
        assertThat(savedPath.getFileName().toString()).isEqualTo("test_image.png");
    }

    @Test
    void store_ShouldThrowInvalidRequestException_WhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.store(emptyFile))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Uploaded file cannot be empty.");
    }

    @Test
    void store_ShouldThrowInvalidRequestException_WhenExtensionIsMissing() {
        MockMultipartFile missingExtFile = new MockMultipartFile(
            "file", 
            "invalid_filename_without_extension", 
            "image/png", 
            "payload".getBytes()
        );

        assertThatThrownBy(() -> storageService.store(missingExtFile))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Invalid file name or missing extension.");
    }
}
