package ch.supsi.imageprocessing.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;   

@Configuration
public class WebClientConfig {

		@Value("${orchestrator.url}")
		private String orchestratorUrl;

		@Bean
		public WebClient orchestratorWebClient() {
				return WebClient.builder()
						.baseUrl(orchestratorUrl)
						.defaultStatusHandler(
								HttpStatusCode::isError,
								response -> response.bodyToMono(String.class)
								.defaultIfEmpty("")
								.flatMap(body -> Mono.error(
										WebClientResponseException.create(
												response.statusCode().value(),
												response.statusCode().toString(),
												response.headers().asHttpHeaders(),
												body.getBytes(StandardCharsets.UTF_8),
												StandardCharsets.UTF_8
												)
										))
								)
						.build();
		}
}
