package ch.supsi.imageprocessing.gateway.client;

import ch.supsi.imageprocessing.common.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
                .uri("/api/users")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .block();
    }

    public List<UserResponse> getUsers() {
        return webClient.get()
                .uri("/api/users")
                .retrieve()
                .bodyToFlux(UserResponse.class)
                .collectList()
                .block();
    }

    public List<ImageResponse> getUserImages(Long userId) {
        return webClient.get()
                .uri("/api/users/{id}/images", userId)
                .retrieve()
                .bodyToFlux(ImageResponse.class)
                .collectList()
                .block();
    }

    public void deleteUser(Long userId) {
        webClient.delete()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    // --- Image ---

    public ImageResponse getImage(Long imageId) {
        return webClient.get()
                .uri("/api/images/{id}", imageId)
                .retrieve()
                .bodyToMono(ImageResponse.class)
                .block();
    }

    public void deleteImage(Long imageId) {
        webClient.delete()
                .uri("/api/images/{id}", imageId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public List<JobResponse> getImageJobs(Long imageId) {
        return webClient.get()
                .uri("/api/images/{id}/jobs", imageId)
                .retrieve()
                .bodyToFlux(JobResponse.class)
                .collectList()
                .block();
    }

    public JobResponse createJob(Long imageId, JobRequest request) {
        return webClient.post()
                .uri("/api/images/{id}/jobs", imageId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JobResponse.class)
                .block();
    }

    // --- Job ---

    public JobResponse getJob(Long jobId) {
        return webClient.get()
                .uri("/api/jobs/{id}", jobId)
                .retrieve()
                .bodyToMono(JobResponse.class)
                .block();
    }

    public JobResponse processJob(Long jobId) {
        return webClient.post()
                .uri("/api/jobs/{id}/process", jobId)
                .retrieve()
                .bodyToMono(JobResponse.class)
                .block();
    }

    public void deleteJob(Long jobId) {
        webClient.delete()
                .uri("/api/jobs/{id}", jobId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
