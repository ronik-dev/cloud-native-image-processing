package ch.supsi.imageprocessing.processor;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.service.StorageService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class ImageProcessor {

		private final Path outputDir;

		@Autowired
		private StorageService ss;

		public ImageProcessor(@Value("${storage.output-dir:/tmp/imageprocessing/outputs}") String storagePath) {
				this.outputDir = Paths.get(storagePath);
				try {
						Files.createDirectories(outputDir);
				} catch (IOException e) {
						throw new RuntimeException("Failed to initialize storage folder", e);
				}
		}

		public String execute(ProcessingJob job) throws IOException, InterruptedException {
				File sourceFile = ss.getResource(job.getImage().getStorageKey()).getFile();

				Path tempOutputFile = Files.createTempFile("ffmpeg-output-", ".tmp");

				try {
						// Run your processing workload (Placeholder copy / Future FFmpeg command)
						// We read from sourceFile and write the fresh output to tempOutputFile
						Files.copy(sourceFile.toPath(), tempOutputFile, StandardCopyOption.REPLACE_EXISTING);

						/* // Future FFmpeg Implementation Example:
						   ProcessBuilder pb = new ProcessBuilder(
						   "ffmpeg", "-i", sourceFile.getAbsolutePath(), tempOutputFile.toString()
						   );
						   Process process = pb.start();
						   if (process.waitFor() != 0) throw new IOException("FFmpeg execution failed");
						   */

						// 4. Stream the *processed temporary output file* into the storage service
						try (InputStream is = Files.newInputStream(tempOutputFile)) {
								ss.storeLocalFile(is, job.getTargetStorageKey());
						}

				} finally {
						Files.deleteIfExists(tempOutputFile);
				}

				return job.getTargetStorageKey();
		}
}
