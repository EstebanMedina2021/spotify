package com.playlist.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Gemini SDK client with an explicit HTTP timeout. Spring AI's own
 * auto-configuration backs off because a {@link Client} bean already exists.
 */
@Configuration
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiClientConfig {

    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                                    @Value("${app.ai.timeout-seconds:5}") int timeoutSeconds) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(timeoutSeconds * 1000).build())
                .build();
    }
}
