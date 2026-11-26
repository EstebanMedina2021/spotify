package com.playlist.recommendation;

import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiRecommendationClient {

    private final FallbackCatalog fallbackCatalog;
    private final String scenario;

    public MockAiClient(
            FallbackCatalog fallbackCatalog,
            @Value("${app.ai.mock.scenario:OK}") String scenario) {
        this.fallbackCatalog = fallbackCatalog;
        this.scenario = scenario;
    }

    @Override
    public CompletableFuture<RecommendationResult> recommend(List<CancionSnapshot> canciones, String promptVersion) {
        return CompletableFuture.supplyAsync(() -> {
            switch (scenario.toUpperCase()) {
                case "TIMEOUT":
                    throw new AiProviderTimeoutException("Mock timeout");
                case "ERROR":
                    throw new AiProviderException("Mock provider error");
                case "AUTH_ERROR":
                    throw new AiProviderAuthException("Mock auth error");
                case "INVALID":
                    throw new AiInvalidResponseException("Mock invalid response");
                case "OK":
                default:
                    String genero = fallbackCatalog.firstGenre(canciones);
                    List<RecomendacionItem> items = fallbackCatalog.suggestionsFor(genero);
                    return RecommendationResult.ok(items, "MOCK", promptVersion);
            }
        });
    }
}