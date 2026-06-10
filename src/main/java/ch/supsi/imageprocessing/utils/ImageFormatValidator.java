package ch.supsi.imageprocessing.utils;

import ch.supsi.imageprocessing.exception.InvalidRequestException;
import ch.supsi.imageprocessing.exception.UnsupportedFileFormatException;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

public class ImageFormatValidator {

		private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");

		public static String validateAndExtractFormat(MultipartFile file) {
				if (file == null || file.isEmpty()) 
						throw new InvalidRequestException("Upload payload contains no file data.");

				try (InputStream is = new BufferedInputStream(file.getInputStream())) {
						String actualMimeType = URLConnection.guessContentTypeFromStream(is);

						if (actualMimeType == null || !ALLOWED_MIME_TYPES.contains(actualMimeType.toLowerCase())) 
								throw new UnsupportedFileFormatException("Invalid file type. Only standard images (JPEG, PNG, WEBP, GIF, BMP) are allowed.");

						return actualMimeType.substring(actualMimeType.indexOf("/") + 1).toLowerCase();

				} catch (IOException e) {
						throw new RuntimeException("Failed to analyze file stream signatures.", e);
				}
		}
}
