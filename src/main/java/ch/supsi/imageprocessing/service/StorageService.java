package ch.supsi.imageprocessing.service;

import ch.supsi.imageprocessing.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
}
