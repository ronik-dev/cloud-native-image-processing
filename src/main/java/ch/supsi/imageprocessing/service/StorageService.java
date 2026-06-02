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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {

		@Value("${storage.upload-dir:/tmp/imageprocessing/uploads}")
		private String uploadDirStr;

		public Path store(MultipartFile file) {
				if (file == null || file.isEmpty()) {
						throw new InvalidRequestException("Uploaded file cannot be empty.");
				}

				String originalFilename = file.getOriginalFilename();
				if (originalFilename == null || !originalFilename.contains(".")) {
						throw new InvalidRequestException("Invalid file name or missing extension.");
				}

				try {
						Path uploadPath = Paths.get(uploadDirStr);
						if (!Files.exists(uploadPath)) {
								Files.createDirectories(uploadPath);
						}

						Path destinationPath = uploadPath.resolve(originalFilename);
						Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

						return destinationPath.toAbsolutePath();
				} catch (IOException e) {
						throw new RuntimeException("Disk I/O execution failure during copy", e);
				}
		}

		public Resource getResource(String absolutePath){
				try {
						Path file = Paths.get(absolutePath);
						Resource resource = new UrlResource(file.toUri());
						if (resource.exists() || resource.isReadable()) {
								return resource;
						} else {
								throw new ResourceNotFoundException("Could not read physical file at: " + absolutePath);
						}
				} catch (MalformedURLException e) {
						throw new ResourceNotFoundException("Could not resolve file path URL layout:\n"+e.getMessage());
				}
		}
}
