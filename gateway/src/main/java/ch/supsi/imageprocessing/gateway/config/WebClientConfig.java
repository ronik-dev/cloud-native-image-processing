package ch.supsi.imageprocessing.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${orchestrator.url}")
    private String orchestratorUrl;

    @Bean
    public WebClient orchestratorWebClient() {
        return WebClient.builder()
                .baseUrl(orchestratorUrl)
                .build();
    }
}
