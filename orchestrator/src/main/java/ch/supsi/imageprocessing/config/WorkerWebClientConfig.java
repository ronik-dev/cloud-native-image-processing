package ch.supsi.imageprocessing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WorkerWebClientConfig {

		@Value("${worker.url:http://localhost:8082}")
		private String workerUrl;

		@Bean
		public WebClient workerWebClient() {
				return WebClient.builder()
						.baseUrl(workerUrl)
						.build();
		}
}
