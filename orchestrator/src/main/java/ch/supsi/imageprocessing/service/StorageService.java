package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.common.exception.InvalidRequestException;
import ch.supsi.imageprocessing.common.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import io.micrometer.observation.annotation.Observed;

import java.net.MalformedURLException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {

		@Value("${storage.data-dir:/tmp/imageprocessing/data}")
		private String dataDirStr;


		@Observed(name="storage.service.store.multipartFile", contextualName = "storing-multipart-file")
		public Path storeMultipartFile(MultipartFile file, String storageKey) {
				if (file == null || file.isEmpty()) {
						throw new InvalidRequestException("Uploaded file cannot be empty.");
				}
				if (storageKey == null || storageKey.trim().isEmpty()){
						throw new InvalidRequestException("Storage key cannot be empty.");
				}

				try (InputStream inputStream = file.getInputStream()) {
						return saveToDisk(inputStream, dataDirStr, storageKey);
				} catch (IOException e) {
						throw new RuntimeException("Failed to read multipart upload stream", e);
				}
		}


		@Observed(name="storage.service.delete", contextualName = "deleting-resource")
		public void deleteResource(String storageKey) {
				if (storageKey == null || storageKey.trim().isEmpty()) {
						throw new InvalidRequestException("Storage key cannot be empty.");
				}
				try{
						Path BaseDir = Paths.get(dataDirStr);
						Path outputFile = BaseDir.resolve(storageKey).normalize();

						if (!outputFile.startsWith(BaseDir)) 
								throw new InvalidRequestException("Invalid storage key path.");

						if (Files.exists(outputFile)) {
								Files.delete(outputFile);
								return;
						}

						throw new ResourceNotFoundException("Could not find physical file to delete in uploads or outputs: " + storageKey);
				} catch (IOException e) {
						throw new RuntimeException("Disk I/O execution failure during resource deletion: " + e.getMessage(), e);
				}
		}


		@Observed(name="storage.service.store.localFile", contextualName = "storing-local-file")
		public Path storeLocalFile(InputStream inputStream, String storageKey) {
				if (inputStream == null) {
						throw new InvalidRequestException("Source input stream cannot be null.");
				}
				if (storageKey == null || storageKey.trim().isEmpty()){
						throw new InvalidRequestException("Storage key cannot be empty.");
				}

				return saveToDisk(inputStream, dataDirStr, storageKey);
		}

		private Path saveToDisk(InputStream inputStream, String baseDirStr, String storageKey) {
				try {
						Path baseDir = Paths.get(baseDirStr);
						Path destinationPath = baseDir.resolve(storageKey).normalize();

						if (!destinationPath.startsWith(baseDir)) {
								throw new InvalidRequestException("Invalid storage key path.");
						}

						Path parentDir = destinationPath.getParent();
						if (parentDir != null && !Files.exists(parentDir)) {
								Files.createDirectories(parentDir);
						}

						Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);

						return destinationPath.toAbsolutePath();
				} catch (IOException e) {
						throw new RuntimeException("Disk I/O execution failure during storage write", e);
				}
		}


		@Observed(name="storage.service.get.resource", contextualName = "return-resource")
		public Resource getResource(String storageKey) {
				if (storageKey == null || storageKey.trim().isEmpty())
						throw new InvalidRequestException("Storage key cannot be empty.");

				try {
						Path outputBaseDir = Paths.get(dataDirStr);
						Path file = outputBaseDir.resolve(storageKey).normalize();

						if (!file.startsWith(outputBaseDir)) {
								throw new InvalidRequestException("Invalid storage key path.");
						}

						Resource resource = new UrlResource(file.toUri());
						if (resource.exists() && resource.isReadable()) {
								return resource;
						}

						throw new ResourceNotFoundException("Could not find physical file in uploads or outputs: " + storageKey);

				} catch (MalformedURLException e) {
						throw new ResourceNotFoundException("Could not resolve file path URL layout: " + e.getMessage());
				}
		}
}
