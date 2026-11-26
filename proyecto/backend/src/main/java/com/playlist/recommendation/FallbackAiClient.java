package com.playlist.recommendation;

import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
            // executeCompletionStage requiere scheduler + Supplier<CompletionStage>
            CompletableFuture<RecommendationResult> future = timeLimiter
                    .executeCompletionStage(scheduler, () -> delegate.recommend(canciones, promptVersion))
                    .toCompletableFuture();

            // Envolver con circuit breaker: si el CB está abierto, lanza CallNotPermittedException
            CompletableFuture<RecommendationResult> withCb = circuitBreaker.executeCompletionStage(
                    () -> future
            ).toCompletableFuture();

            return withCb.handle((result, throwable) -> {
                if (throwable == null) {
                    return result;
                }
                return fallback(canciones, promptVersion, throwable);
            });
        } catch (CallNotPermittedException ex) {
            return CompletableFuture.completedFuture(fallback(canciones, promptVersion, ex));
        } catch (Exception ex) {
            return CompletableFuture.completedFuture(fallback(canciones, promptVersion, ex));
        }
    }

    private RecommendationResult fallback(List<CancionSnapshot> canciones, String promptVersion, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String motivo;
        if (cause instanceof AiProviderTimeoutException || cause instanceof TimeoutException) {
            motivo = "TIMEOUT";
        } else if (cause instanceof AiProviderAuthException) {
            motivo = "PROVIDER_AUTH_ERROR";
        } else if (cause instanceof AiProviderException) {
            motivo = "PROVIDER_DOWN";
        } else if (cause instanceof AiInvalidResponseException) {
            motivo = "INVALID_RESPONSE";
        } else if (cause instanceof CallNotPermittedException) {
            motivo = "CIRCUIT_OPEN";
        } else {
            motivo = "PROVIDER_DOWN";
        }

        log.warn("AI fallback triggered: motivo={}, cause={}", motivo, cause.getClass().getSimpleName());

        String genero = fallbackCatalog.firstGenre(canciones);
        List<RecomendacionItem> items = fallbackCatalog.suggestionsFor(genero);
        return RecommendationResult.fallback(items, motivo, promptVersion);
    }

    private Throwable unwrap(Throwable t) {
        while (t.getCause() != null && t != t.getCause()) {
            t = t.getCause();
        }
        return t;
    }
}