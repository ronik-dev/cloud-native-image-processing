package ch.supsi.imageprocessing.gateway.client;

import ch.supsi.imageprocessing.common.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;   
import org.springframework.http.client.MultipartBodyBuilder;   
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Component
public class OrchestratorClient {

		private final WebClient webClient;

		public OrchestratorClient(WebClient orchestratorWebClient) {
				this.webClient = orchestratorWebClient;
		}

		// --- User ---

		public UserResponse createUser(UserRequest request) {
				return webClient.post()
						.uri("/users")
						.bodyValue(request)
						.retrieve()
						.bodyToMono(UserResponse.class)
						.block();
		}

		public List<UserResponse> getUsers() {
				return webClient.get()
						.uri("/users")
						.retrieve()
						.bodyToMono(new ParameterizedTypeReference<List<UserResponse>>() {})
						.block();
		}

		public List<ImageResponse> getUserImages(Long userId) {
				return webClient.get()
						.uri("/users/{id}/images", userId)
						.retrieve()
						.bodyToMono(new ParameterizedTypeReference<List<ImageResponse>>() {})
						.block();
		}

		public void deleteUser(Long userId) {
				webClient.delete()
						.uri("/users/{id}", userId)
						.retrieve()
						.toBodilessEntity()
						.block();
		}

		// --- Image ---

		public ImageResponse getImage(Long imageId) {
				return webClient.get()
						.uri("/images/{id}", imageId)
						.retrieve()
						.bodyToMono(ImageResponse.class)
						.block();
		}

		public void deleteImage(Long imageId) {
				webClient.delete()
						.uri("/images/{id}", imageId)
						.retrieve()
						.toBodilessEntity()
						.block();
		}

		public List<JobResponse> getImageJobs(Long imageId) {
				return webClient.get()
						.uri("/images/{id}/jobs", imageId)
						.retrieve()
						.bodyToMono(new ParameterizedTypeReference<List<JobResponse>>() {})
						.block();
		}

		public JobResponse createJob(Long imageId, JobRequest request) {
				return webClient.post()
						.uri("/images/{id}/jobs", imageId)
						.bodyValue(request)
						.retrieve()
						.bodyToMono(JobResponse.class)
						.block();
		}

		// --- Job ---

		public JobResponse getJob(Long jobId) {
				return webClient.get()
						.uri("/jobs/{id}", jobId)
						.retrieve()
						.bodyToMono(JobResponse.class)
						.block();
		}

		public JobResponse processJob(Long jobId) {
				return webClient.post()
						.uri("/jobs/{id}/process", jobId)
						.retrieve()
						.bodyToMono(JobResponse.class)
						.block();
		}

		public void deleteJob(Long jobId) {
				webClient.delete()
						.uri("/jobs/{id}", jobId)
						.retrieve()
						.toBodilessEntity()
						.block();
		}

		public ResponseEntity<byte[]> downloadJobResult(Long jobId) {
				return webClient.get()
						.uri("/jobs/{id}/result", jobId)
						.retrieve()
						.toEntity(byte[].class)
						.block();
		}

		public ImageResponse uploadImage(MultipartFile file, Long userId) {
				MultipartBodyBuilder builder = new MultipartBodyBuilder();

				try {
						builder.part("file", file.getBytes())
								.filename(file.getOriginalFilename())
								.contentType(MediaType.parseMediaType(
														file.getContentType() != null 
														? file.getContentType() 
														: "application/octet-stream"
														));
				} catch (IOException e) {
						throw new UncheckedIOException("Failed to read upload file", e);
				}

				builder.part("userId", userId.toString());

				return webClient.post()
						.uri("/images")
						.contentType(MediaType.MULTIPART_FORM_DATA)
						.bodyValue(builder.build())
						.retrieve()
						.bodyToMono(ImageResponse.class)
						.block();
		}
}
