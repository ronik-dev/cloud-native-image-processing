package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {

		@Value("${storage.upload-dir:/tmp/imageprocessing/uploads}")
		private String uploadDirStr;

		@Value("${storage.output-dir:/tmp/imageprocessing/outputs}")
		private String outputDirStr;

		public Path storeMultipartFile(MultipartFile file, String storageKey) {
				if (file == null || file.isEmpty()) {
						throw new InvalidRequestException("Uploaded file cannot be empty.");
				}
				if (storageKey == null || storageKey.trim().isEmpty()){
						throw new InvalidRequestException("Storage key cannot be empty.");
				}

				try (InputStream inputStream = file.getInputStream()) {
						return saveToDisk(inputStream, uploadDirStr, storageKey);
				} catch (IOException e) {
						throw new RuntimeException("Failed to read multipart upload stream", e);
				}
		}

		public Path storeLocalFile(InputStream inputStream, String storageKey) {
				if (inputStream == null) {
						throw new InvalidRequestException("Source input stream cannot be null.");
				}
				if (storageKey == null || storageKey.trim().isEmpty()){
						throw new InvalidRequestException("Storage key cannot be empty.");
				}

				return saveToDisk(inputStream, uploadDirStr, storageKey);
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

		public Resource getResource(String storageKey) {
				if (storageKey == null || storageKey.trim().isEmpty())
						throw new InvalidRequestException("Storage key cannot be empty.");

				try {
						Path outputBaseDir = Paths.get(outputDirStr);
						Path outputFile = outputBaseDir.resolve(storageKey).normalize();

						if (!outputFile.startsWith(outputBaseDir)) {
								throw new InvalidRequestException("Invalid storage key path.");
						}

						Resource outputResource = new UrlResource(outputFile.toUri());
						if (outputResource.exists() && outputResource.isReadable()) {
								return outputResource;
						}

						Path uploadBaseDir = Paths.get(uploadDirStr);
						Path uploadFile = uploadBaseDir.resolve(storageKey).normalize();

						if (!uploadFile.startsWith(uploadBaseDir)) {
								throw new InvalidRequestException("Invalid storage key path.");
						}

						Resource uploadResource = new UrlResource(uploadFile.toUri());
						if (uploadResource.exists() && uploadResource.isReadable()) {
								return uploadResource;
						}

						throw new ResourceNotFoundException("Could not find physical file in uploads or outputs: " + storageKey);

				} catch (MalformedURLException e) {
						throw new ResourceNotFoundException("Could not resolve file path URL layout: " + e.getMessage());
				}
		}
}
