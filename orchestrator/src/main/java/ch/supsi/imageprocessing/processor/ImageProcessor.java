package ch.supsi.imageprocessing.processor;

import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.common.enums.JobType;
import ch.supsi.imageprocessing.service.StorageService;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.observation.annotation.Observed;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class ImageProcessor {

		private final Path outputDir;

		@Autowired
		private StorageService ss;

		public ImageProcessor(@Value("${storage.data-dir:/tmp/imageprocessing/data}") String storagePath) {
				this.outputDir = Paths.get(storagePath);
				try {
						Files.createDirectories(outputDir);
				} catch (IOException e) {
						throw new RuntimeException("Failed to initialize storage folder", e);
				}
		}


		@Observed(name = "image.processor.execute", contextualName = "processing-image-filters")
		public String execute(ProcessingJob job) throws IOException, InterruptedException {
				File sourceFile = ss.getResource(job.getImage().getStorageKey()).getFile();

				Path tempOutputFile = Files.createTempFile("ffmpeg-output-"+UUID.randomUUID().toString(), "." + job.getTargetFormat());

				try {
						switch (job.getType()) {
								case JobType.FORMAT_CONVERSION:
										ffmpegConvert(sourceFile, tempOutputFile);
										break;
								case JobType.BACKGROUND_REMOVAL:
										Thread.sleep(10000);
										Files.copy(sourceFile.toPath(), tempOutputFile, StandardCopyOption.REPLACE_EXISTING);
										break;
								default:
										throw new UnsupportedOperationException("Job type " + job.getType() + " is not yet implemented.");
						}
						try (InputStream is = Files.newInputStream(tempOutputFile)) {
								ss.storeLocalFile(is, job.getTargetStorageKey());
						}
				} finally {
						Files.deleteIfExists(tempOutputFile);
				}

				return job.getTargetStorageKey();
		}

		private void ffmpegConvert(File input, Path output) throws IOException, InterruptedException {
				ProcessBuilder pb = new ProcessBuilder(
								"ffmpeg",
								"-y", 
								"-i", input.getAbsolutePath(),
								output.toAbsolutePath().toString()
								);

				pb.redirectErrorStream(true);
				pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
				Process process = pb.start();
				int exitCode = process.waitFor();

				if (exitCode != 0) {
						throw new IOException("FFmpeg process execution failed with termination exit code: " + exitCode);
				}
		}
}
