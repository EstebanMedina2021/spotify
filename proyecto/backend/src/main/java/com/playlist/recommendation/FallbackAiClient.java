package com.playlist.recommendation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Decorator that adds timeout, circuit breaker and a deterministic fallback to any provider.
 * It never propagates a provider failure: the returned future always completes normally.
 */
@Slf4j
@Component
@Primary
public class FallbackAiClient implements AiRecommendationClient {

    private final AiRecommendationClient delegate;
    private final FallbackCatalog fallbackCatalog;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final ScheduledExecutorService scheduler;

    public FallbackAiClient(
            AiRecommendationClient delegate,
            FallbackCatalog fallbackCatalog,
            TimeLimiter aiTimeLimiter,
            CircuitBreaker aiCircuitBreaker,
            ScheduledExecutorService aiTimeoutScheduler) {
        this.delegate = delegate;
        this.fallbackCatalog = fallbackCatalog;
        this.timeLimiter = aiTimeLimiter;
        this.circuitBreaker = aiCircuitBreaker;
        this.scheduler = aiTimeoutScheduler;
    }

    @Override
    public CompletableFuture<RecommendationResult> recommend(List<CancionSnapshot> canciones, String promptVersion) {
        try {
            // The breaker is the outer layer: when it is OPEN the provider call is never started.
            // The time limit sits inside so a timeout also counts as a breaker failure.
            return circuitBreaker
                    .executeCompletionStage(() -> timeLimiter.executeCompletionStage(
                            scheduler, () -> delegate.recommend(canciones, promptVersion)))
                    .toCompletableFuture()
                    .handle((result, throwable) ->
                            throwable == null ? result : fallback(canciones, promptVersion, throwable));
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(fallback(canciones, promptVersion, ex));
        }
    }

    private RecommendationResult fallback(List<CancionSnapshot> canciones, String promptVersion, Throwable throwable) {
        String motivo = AiFailureClassifier.motivoFor(throwable);
        log.warn("AI fallback triggered: motivo={}, cause={}", motivo, throwable.getClass().getSimpleName());

        String genero = fallbackCatalog.firstGenre(canciones);
        return RecommendationResult.fallback(fallbackCatalog.suggestionsFor(genero), motivo, promptVersion);
    }
}
