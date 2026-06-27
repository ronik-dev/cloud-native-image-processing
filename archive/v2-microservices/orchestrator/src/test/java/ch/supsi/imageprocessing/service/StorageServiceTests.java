package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.common.exception.InvalidRequestException;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

		private StorageService storageService;

		@TempDir
		Path sharedDataTempDir;

		@BeforeEach
		void setUp() {
				storageService = new StorageService();
				ReflectionTestUtils.setField(storageService, "dataDirStr", sharedDataTempDir.toString());
		}

		// ==========================================
		// storeMultipartFile
		// ==========================================

		@Test
		void storeMultipartFile_ShouldSaveFileToDisk_WhenPayloadIsValid() {
				MockMultipartFile validFile = new MockMultipartFile(
								"file", "test_image.png", "image/png", "fake-binary-data".getBytes());

				Path savedPath = storageService.storeMultipartFile(validFile, "kusebciuawbeo");

				assertThat(savedPath).isNotNull();
				assertThat(Files.exists(savedPath)).isTrue();
				assertThat(savedPath.getFileName().toString()).isEqualTo("kusebciuawbeo");
		}

		@Test
		void storeMultipartFile_ShouldThrowInvalidRequestException_WhenFileIsNull() {
				assertThatThrownBy(() -> storageService.storeMultipartFile(null, "key"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Uploaded file cannot be empty.");
		}

		@Test
		void storeMultipartFile_ShouldThrowInvalidRequestException_WhenFileIsEmpty() {
				MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

				assertThatThrownBy(() -> storageService.storeMultipartFile(emptyFile, "kajsiuabcusbiue"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Uploaded file cannot be empty.");
		}

		@Test
		void storeMultipartFile_ShouldThrowInvalidRequestException_WhenKeyIsEmpty() {
				MockMultipartFile validFile = new MockMultipartFile("file", "img.png", "image/png", "data".getBytes());

				assertThatThrownBy(() -> storageService.storeMultipartFile(validFile, "   "))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Storage key cannot be empty.");
		}

		@Test
		void storeMultipartFile_ShouldThrowInvalidRequestException_WhenDirectoryTraversalIsAttempted() {
				MockMultipartFile validFile = new MockMultipartFile("file", "img.png", "image/png", "data".getBytes());

				assertThatThrownBy(() -> storageService.storeMultipartFile(validFile, "../../../etc/passwd"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Invalid storage key path.");
		}

		// ==========================================
		// storeLocalFile
		// ==========================================

		@Test
		void storeLocalFile_ShouldSaveStreamToDisk_WhenValid() {
				InputStream sourceStream = new ByteArrayInputStream("ai-workload-data".getBytes());

				Path savedPath = storageService.storeLocalFile(sourceStream, "processed/output.png");

				assertThat(savedPath).isNotNull();
				assertThat(Files.exists(savedPath)).isTrue();
				assertThat(savedPath).startsWith(sharedDataTempDir);
		}

		@Test
		void storeLocalFile_ShouldThrowInvalidRequestException_WhenStreamIsNull() {
				assertThatThrownBy(() -> storageService.storeLocalFile(null, "output.png"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Source input stream cannot be null.");
		}

		@Test
		void storeLocalFile_ShouldThrowInvalidRequestException_WhenKeyIsBlank() {
				InputStream sourceStream = new ByteArrayInputStream("data".getBytes());

				assertThatThrownBy(() -> storageService.storeLocalFile(sourceStream, "  "))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Storage key cannot be empty.");
		}

		@Test
		void storeLocalFile_ShouldThrowInvalidRequestException_WhenTraversalIsAttempted() {
				InputStream sourceStream = new ByteArrayInputStream("data".getBytes());

				assertThatThrownBy(() -> storageService.storeLocalFile(sourceStream, "../escape.png"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Invalid storage key path.");
		}

		// ==========================================
		// deleteResource
		// ==========================================

		@Test
		void deleteResource_ShouldRemoveFile_WhenItExists() throws IOException {
				String storageKey = "to-delete.png";
				Path file = sharedDataTempDir.resolve(storageKey);
				Files.writeString(file, "bytes");
				assertThat(Files.exists(file)).isTrue();

				storageService.deleteResource(storageKey);

				assertThat(Files.exists(file)).isFalse();
		}

		@Test
		void deleteResource_ShouldThrowResourceNotFound_WhenFileMissing() {
				assertThatThrownBy(() -> storageService.deleteResource("nope.png"))
						.isInstanceOf(ResourceNotFoundException.class)
						.hasMessageContaining("Could not find physical file to delete");
		}

		@Test
		void deleteResource_ShouldThrowInvalidRequestException_WhenKeyIsBlank() {
				assertThatThrownBy(() -> storageService.deleteResource("   "))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Storage key cannot be empty.");
		}

		@Test
		void deleteResource_ShouldThrowInvalidRequestException_WhenTraversalIsAttempted() {
				assertThatThrownBy(() -> storageService.deleteResource("../../etc/passwd"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Invalid storage key path.");
		}

		// ==========================================
		// getResource
		// ==========================================

		@Test
		void getResource_ShouldReturnResource_WhenFileExistsAndIsReadable() throws IOException {
				String storageKey = "results/target_image.png";
				Path physicalFile = sharedDataTempDir.resolve(storageKey);
				Files.createDirectories(physicalFile.getParent());
				Files.writeString(physicalFile, "processed-pixels");

				Resource resource = storageService.getResource(storageKey);

				assertThat(resource).isNotNull();
				assertThat(resource.exists()).isTrue();
				assertThat(resource.isReadable()).isTrue();
		}

		@Test
		void getResource_ShouldThrowResourceNotFoundException_WhenFileDoesNotExist() {
				assertThatThrownBy(() -> storageService.getResource("missing-file.png"))
						.isInstanceOf(ResourceNotFoundException.class)
						.hasMessageContaining("Could not find physical file in uploads or outputs: missing-file.png");
		}

		@Test
		void getResource_ShouldThrowInvalidRequestException_WhenKeyIsBlank() {
				assertThatThrownBy(() -> storageService.getResource("  "))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Storage key cannot be empty.");
		}

		@Test
		void getResource_ShouldThrowInvalidRequestException_WhenTraversalIsAttempted() {
				assertThatThrownBy(() -> storageService.getResource("../outside.png"))
						.isInstanceOf(InvalidRequestException.class)
						.hasMessageContaining("Invalid storage key path.");
		}
}
