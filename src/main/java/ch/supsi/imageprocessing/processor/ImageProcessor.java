package ch.supsi.imageprocessing.processor;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class ImageProcessor {

		private final Path outputDir;

		public ImageProcessor(@Value("${storage.output-dir:/tmp/imageprocessing/outputs}") String storagePath) {
				this.outputDir = Paths.get(storagePath);
				try {
						Files.createDirectories(outputDir);
				} catch (IOException e) {
						throw new RuntimeException("Failed to initialize storage folder", e);
				}
		}

		public String execute(ProcessingJob job) throws IOException, InterruptedException {
				Path sourcePath = Paths.get(job.getImage().getStoragePath());
				if (!Files.exists(sourcePath)) {
						throw new IOException("Source image file missing at: " + sourcePath);
				}

				Path destinationPath = outputDir.resolve(job.getOutputName());

				// Monolith placeholder: Simulate FFmpeg conversion via a fast NIO copy
				Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

				/* // Future FFmpeg Implementation Example:
				   ProcessBuilder pb = new ProcessBuilder(
				   "ffmpeg", "-i", sourcePath.toString(), destinationPath.toString()
				   );
				   Process process = pb.start();
				   int exitCode = process.waitFor();
				   if (exitCode != 0) throw new IOException("FFmpeg failed with exit code " + exitCode);
				   */

				return destinationPath.toAbsolutePath().toString();
		}
}
