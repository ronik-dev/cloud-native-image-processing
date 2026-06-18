package ch.supsi.imageprocessing.client;

import ch.supsi.imageprocessing.common.dto.ConvertFormatRequest;
import ch.supsi.imageprocessing.common.dto.WorkerResponse;
import ch.supsi.imageprocessing.common.dto.RemoveBackgroundRequest;
import ch.supsi.imageprocessing.common.dto.DetectObjectsRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WorkerClient{

		private final WebClient webClient;

		public WorkerClient(WebClient workerWebClient) {
				this.webClient = workerWebClient;
		}


		public WorkerResponse convertFormat(ConvertFormatRequest request) {
				System.out.println("Sending to worker: " + request.toString());
				return webClient.post()
						.uri("/convert_format")
						.bodyValue(request)
						.retrieve()
						.bodyToMono(WorkerResponse.class)
						.block();
		}

		public WorkerResponse removeBackground(RemoveBackgroundRequest request) {
				return webClient.post()
						.uri("/remove_background")
						.bodyValue(request)
						.retrieve()
						.bodyToMono(WorkerResponse.class)
						.block();
		}

		public WorkerResponse detectObjects(DetectObjectsRequest request) {
				return webClient.post()
						.uri("/detect_objects")
						.bodyValue(request)
						.retrieve()
						.bodyToMono(WorkerResponse.class)
						.block();
		}
}
