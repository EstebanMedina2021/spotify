package com.playlist.recommendation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the real Ollama wiring (Spring AI + RestClient timeouts) against WireMock.
 * Verifies context startup, HTTP timeout, error mapping and response validation.
 */
@SpringBootTest(properties = {
        "app.ai.provider=ollama",
        "spring.ai.model.chat=ollama",
        "OLLAMA_BASE_URL=configured",
        "app.ai.timeout-seconds=1"
})
@ActiveProfiles("test")
class OllamaAiClientIT {

    private static WireMockServer wireMock;

    @Autowired
    private OllamaAiClient client;

    private final List<CancionSnapshot> songs = List.of(new CancionSnapshot("Bohemian Rhapsody", "Queen", "Rock"));

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void ollamaUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    private static String chatBody(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"model\":\"llama3.2\",\"created_at\":\"2026-01-01T00:00:00Z\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},"
                + "\"done\":true,\"done_reason\":\"stop\"}";
    }

    @Test
    void validResponse_returnsRecommendations() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/api/chat")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(chatBody("{\"recomendaciones\":[{\"titulo\":\"Under Pressure\",\"artista\":\"Queen\"}]}"))));

        RecommendationResult result = client.recommend(songs, "v1").get();

        assertThat(result.fuente()).isEqualTo("OLLAMA");
        assertThat(result.recomendaciones()).hasSize(1);
    }

    @Test
    void slowResponse_mapsToTimeout() {
        wireMock.stubFor(post(urlPathEqualTo("/api/chat")).willReturn(aResponse()
                .withFixedDelay(3000)
                .withHeader("Content-Type", "application/json")
                .withBody(chatBody("{}"))));

        assertThatThrownBy(() -> client.recommend(songs, "v1").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(AiProviderTimeoutException.class);
    }

    @Test
    void http401_mapsToAuthError() {
        wireMock.stubFor(post(urlPathEqualTo("/api/chat")).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.recommend(songs, "v1").get())
                .hasCauseInstanceOf(AiProviderAuthException.class);
    }

    @Test
    void http500_mapsToProviderError() {
        wireMock.stubFor(post(urlPathEqualTo("/api/chat")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.recommend(songs, "v1").get())
                .hasCauseExactlyInstanceOf(AiProviderException.class);
    }

    @Test
    void garbageContent_mapsToInvalidResponse() {
        wireMock.stubFor(post(urlPathEqualTo("/api/chat")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(chatBody("sorry, I cannot help with that"))));

        assertThatThrownBy(() -> client.recommend(songs, "v1").get())
                .hasCauseInstanceOf(AiInvalidResponseException.class);
    }
}
