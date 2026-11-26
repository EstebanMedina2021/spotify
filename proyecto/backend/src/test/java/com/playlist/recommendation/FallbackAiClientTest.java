package com.playlist.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.playlist.config.ResilienceConfig;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/** Exercises the real decorator (timeout + circuit breaker + fallback), with realistic wrapped causes. */
class FallbackAiClientTest {

    private final ResilienceConfig config = new ResilienceConfig();
    private final FallbackCatalog catalog = new FallbackCatalog();
    private final List<CancionSnapshot> rockSongs = List.of(new CancionSnapshot("Bohemian Rhapsody", "Queen", "rock"));

    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = config.aiTimeoutScheduler();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private FallbackAiClient clientFor(AiRecommendationClient delegate, TimeLimiter timeLimiter, CircuitBreaker breaker) {
        return new FallbackAiClient(delegate, catalog, timeLimiter, breaker, scheduler);
    }

    private FallbackAiClient clientFor(AiRecommendationClient delegate) {
        return clientFor(delegate, config.aiTimeLimiter(5), config.aiCircuitBreaker());
    }

    private RecommendationResult recommend(FallbackAiClient client) throws Exception {
        return client.recommend(rockSongs, "v1").get(5, TimeUnit.SECONDS);
    }

    private static AiRecommendationClient failingWith(RuntimeException error) {
        return (songs, version) -> CompletableFuture.failedFuture(error);
    }

    @Test
    void successfulProvider_returnsTheProviderResultUntouched() throws Exception {
        // Arrange
        RecommendationResult ok = RecommendationResult.ok(
                List.of(new RecomendacionItem("Under Pressure", "Queen", "rock", "x")), "GEMINI", "v1");
        FallbackAiClient client = clientFor((songs, version) -> CompletableFuture.completedFuture(ok));

        // Act
        RecommendationResult result = recommend(client);

        // Assert
        assertThat(result).isEqualTo(ok);
        assertThat(result.fallback()).isFalse();
        assertThat(result.motivoFallback()).isNull();
    }

    @Test
    void timeoutWrappingASocketTimeout_isReportedAsTimeout() throws Exception {
        // Arrange: this is how the real HTTP client reports a read timeout
        FallbackAiClient client = clientFor(failingWith(
                new AiProviderTimeoutException("timeout", new SocketTimeoutException("Read timed out"))));

        // Act
        RecommendationResult result = recommend(client);

        // Assert
        assertThat(result.fallback()).isTrue();
        assertThat(result.motivoFallback()).isEqualTo("TIMEOUT");
        assertThat(result.fuente()).isEqualTo("FALLBACK");
    }

    @Test
    void authErrorWrappingAnHttp401_isReportedAsProviderAuthError() throws Exception {
        FallbackAiClient client = clientFor(failingWith(
                new AiProviderAuthException("auth", new HttpClientErrorException(HttpStatus.UNAUTHORIZED))));

        RecommendationResult result = recommend(client);

        assertThat(result.motivoFallback()).isEqualTo("PROVIDER_AUTH_ERROR");
    }

    @Test
    void providerErrorWrappingAnHttp502_isReportedAsProviderDown() throws Exception {
        FallbackAiClient client = clientFor(failingWith(
                new AiProviderException("down", new HttpClientErrorException(HttpStatus.BAD_GATEWAY))));

        RecommendationResult result = recommend(client);

        assertThat(result.motivoFallback()).isEqualTo("PROVIDER_DOWN");
    }

    @Test
    void invalidResponse_isReportedAsInvalidResponse() throws Exception {
        FallbackAiClient client = clientFor(failingWith(
                new AiInvalidResponseException("bad json", new IllegalStateException("parse"))));

        RecommendationResult result = recommend(client);

        assertThat(result.motivoFallback()).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    void providerThatNeverAnswers_isCutByTheTimeLimiterAndReportedAsTimeout() throws Exception {
        // Arrange
        TimeLimiter shortLimit = TimeLimiter.of("probe", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(150)).cancelRunningFuture(true).build());
        FallbackAiClient client = clientFor((songs, version) -> new CompletableFuture<>(),
                shortLimit, config.aiCircuitBreaker());

        // Act
        RecommendationResult result = recommend(client);

        // Assert
        assertThat(result.motivoFallback()).isEqualTo("TIMEOUT");
    }

    @Test
    void providerThatThrowsSynchronously_neverPropagatesAndFallsBack() throws Exception {
        FallbackAiClient client = clientFor((songs, version) -> {
            throw new IllegalStateException("boom");
        });

        RecommendationResult result = recommend(client);

        assertThat(result.fallback()).isTrue();
        assertThat(result.motivoFallback()).isEqualTo("PROVIDER_DOWN");
    }

    @Test
    void openCircuit_doesNotCallTheProvider_andReportsCircuitOpen() throws Exception {
        // Arrange
        CircuitBreaker breaker = config.aiCircuitBreaker();
        breaker.transitionToOpenState();
        AtomicInteger providerCalls = new AtomicInteger();
        FallbackAiClient client = clientFor((songs, version) -> {
            providerCalls.incrementAndGet();
            return CompletableFuture.completedFuture(RecommendationResult.ok(List.of(), "X", "v1"));
        }, config.aiTimeLimiter(5), breaker);

        // Act
        RecommendationResult result = recommend(client);

        // Assert
        assertThat(result.motivoFallback()).isEqualTo("CIRCUIT_OPEN");
        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void repeatedFailures_openTheCircuit_andStopCallingTheProvider() throws Exception {
        // Arrange: window of 10 calls, 50 % threshold (ResilienceConfig)
        CircuitBreaker breaker = config.aiCircuitBreaker();
        AtomicInteger providerCalls = new AtomicInteger();
        FallbackAiClient client = clientFor((songs, version) -> {
            providerCalls.incrementAndGet();
            return CompletableFuture.failedFuture(new AiProviderException("down"));
        }, config.aiTimeLimiter(5), breaker);

        // Act
        for (int i = 0; i < 10; i++) {
            recommend(client);
        }
        RecommendationResult afterOpen = recommend(client);

        // Assert
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(afterOpen.motivoFallback()).isEqualTo("CIRCUIT_OPEN");
        assertThat(providerCalls).hasValue(10);
    }

    @Test
    void fallbackSuggestions_areDeterministicAndFollowTheGenre() throws Exception {
        FallbackAiClient client = clientFor(failingWith(new AiProviderException("down")));

        RecommendationResult first = recommend(client);
        RecommendationResult second = recommend(client);

        assertThat(first.recomendaciones()).isEqualTo(catalog.suggestionsFor("rock"));
        assertThat(second.recomendaciones()).isEqualTo(first.recomendaciones());
    }
}
