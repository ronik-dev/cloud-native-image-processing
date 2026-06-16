package ch.supsi.imageprocessing.config;

import ch.supsi.imageprocessing.common.dto.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    @Value("${worker.url}")
    private String orchestratorUrl;

    @Bean
    public WebClient orchestratorWebClient() {
        return WebClient.builder()
                .baseUrl(orchestratorUrl)
                .defaultStatusHandler(
                    HttpStatusCode::isError,
                    response -> response.bodyToMono(ErrorResponse.class)
                        .flatMap(error -> Mono.error(
                            new WebClientResponseException(
                                error.message(),
                                response.statusCode().value(),
                                response.statusCode().toString(),
                                null, null, null
                            )
                        ))
                )
                .build();
    }
}
