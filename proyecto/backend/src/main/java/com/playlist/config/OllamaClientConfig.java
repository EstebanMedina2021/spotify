package com.playlist.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Supplies the RestClient.Builder used by Spring AI's Ollama client with explicit
 * connect/read timeouts (03 §5.1, 04 §9). Boot's auto-configured timeouts were not
 * applied to Spring AI's client, so this is set explicitly.
 */
@Configuration
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama")
public class OllamaClientConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder(@Value("${app.ai.timeout-seconds:5}") int readTimeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().requestFactory(factory);
    }
}
