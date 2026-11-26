package com.playlist.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.playlist.playlist.PlaylistService;
import com.playlist.playlist.dto.CancionResponse;
import com.playlist.playlist.dto.PlaylistResponse;
import com.playlist.recommendation.dto.RecommendationResponse;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final UUID OWNER_A = UUID.randomUUID();
    private static final UUID OWNER_B = UUID.randomUUID();

    @Mock private PlaylistService playlistService;
    @Mock private AiRecommendationClient aiClient;

    private final Cache<String, RecommendationResult> cache = Caffeine.newBuilder().maximumSize(100).build();
    private final AtomicInteger providerCalls = new AtomicInteger();
    private RecommendationService service;

    @BeforeEach
    void setUp() throws Exception {
        PromptTemplates templates = new PromptTemplates(new ClassPathResource("prompts/recommendations-v1.txt"));
        templates.load();
        service = new RecommendationService(playlistService, aiClient, templates, new FallbackCatalog(), cache, "mock");

        UUID playlistId = UUID.randomUUID();
        PlaylistResponse rock = new PlaylistResponse(playlistId, "Rock", null,
                List.of(new CancionResponse(UUID.randomUUID(), "Bohemian Rhapsody", "Queen", null, null, "rock")),
                Instant.now(), Instant.now());
        lenient().when(playlistService.obtener("Rock", OWNER_A)).thenReturn(rock);
        lenient().when(playlistService.obtener("Rock", OWNER_B)).thenReturn(rock);
    }

    private static RecommendationResult ok() {
        return RecommendationResult.ok(List.of(new RecomendacionItem("Under Pressure", "Queen", "rock", "x")), "GEMINI", "v1");
    }

    private void providerAnswers(CompletableFuture<RecommendationResult> future) {
        when(aiClient.recommend(any(), any())).thenAnswer(invocation -> {
            providerCalls.incrementAndGet();
            return future;
        });
    }

    @Test
    void identicalConcurrentRequests_callTheProviderOnlyOnce() throws Exception {
        // Arrange: a slow provider so all threads overlap
        when(aiClient.recommend(any(), any())).thenAnswer(invocation -> {
            providerCalls.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> {
                sleep(300);
                return ok();
            });
        });
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RecommendationResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.recomendar("Rock", OWNER_A);
            }));
        }

        // Act
        start.countDown();
        List<RecommendationResponse> responses = new ArrayList<>();
        for (Future<RecommendationResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        // Assert
        assertThat(providerCalls).hasValue(1);
        assertThat(responses).allMatch(r -> !r.fallback() && "GEMINI".equals(r.fuente()));
    }

    @Test
    void successfulResult_isServedFromCacheOnTheSecondCall() {
        providerAnswers(CompletableFuture.completedFuture(ok()));

        service.recomendar("Rock", OWNER_A);
        RecommendationResponse second = service.recomendar("Rock", OWNER_A);

        assertThat(providerCalls).hasValue(1);
        assertThat(second.fallback()).isFalse();
        assertThat(second.mensaje()).isNull();
    }

    @Test
    void fallbackResult_isReturnedButNeverCached() {
        RecommendationResult fallback = RecommendationResult.fallback(
                List.of(new RecomendacionItem("Blinding Lights", "The Weeknd", "pop", "x")), "TIMEOUT", "v1");
        providerAnswers(CompletableFuture.completedFuture(fallback));

        RecommendationResponse first = service.recomendar("Rock", OWNER_A);
        service.recomendar("Rock", OWNER_A);

        assertThat(first.fallback()).isTrue();
        assertThat(first.motivoFallback()).isEqualTo("TIMEOUT");
        assertThat(first.mensaje()).contains("no respondió a tiempo");
        assertThat(providerCalls).hasValue(2);
    }

    @Test
    void differentOwners_neverShareCachedRecommendations() {
        providerAnswers(CompletableFuture.completedFuture(ok()));

        service.recomendar("Rock", OWNER_A);
        service.recomendar("Rock", OWNER_B);

        assertThat(providerCalls).hasValue(2);
    }

    @Test
    void emptyPlaylist_doesNotCallTheProvider() {
        when(playlistService.obtener("Vacia", OWNER_A)).thenReturn(
                new PlaylistResponse(UUID.randomUUID(), "Vacia", null, List.of(), Instant.now(), Instant.now()));

        RecommendationResponse response = service.recomendar("Vacia", OWNER_A);

        verifyNoInteractions(aiClient);
        assertThat(response.recomendaciones()).isEmpty();
        assertThat(response.fallback()).isFalse();
        assertThat(response.motivoFallback()).isNull();
        assertThat(response.mensaje()).contains("no contiene canciones");
    }

    @Test
    void providerFailureThatEscapesTheDecorator_becomesAFallbackNotAnError() {
        providerAnswers(CompletableFuture.failedFuture(
                new AiProviderTimeoutException("slow", new SocketTimeoutException("Read timed out"))));

        RecommendationResponse response = service.recomendar("Rock", OWNER_A);

        assertThat(response.fallback()).isTrue();
        assertThat(response.motivoFallback()).isEqualTo("TIMEOUT");
        assertThat(response.recomendaciones()).isNotEmpty();
    }

    @Test
    void songsTheUserAlreadyHas_areRemovedFromTheModelAnswer() {
        RecommendationResult answer = RecommendationResult.ok(List.of(
                new RecomendacionItem("Bohemian Rhapsody", "Queen", "rock", "already in the list"),
                new RecomendacionItem("Under Pressure - Queen", "Queen", "rock", "new")), "OLLAMA", "v2");
        providerAnswers(CompletableFuture.completedFuture(answer));

        RecommendationResponse response = service.recomendar("Rock", OWNER_A);

        assertThat(response.fallback()).isFalse();
        assertThat(response.recomendaciones()).extracting(RecomendacionItem::titulo).containsExactly("Under Pressure");
    }

    @Test
    void ifTheModelOnlyRepeatsKnownSongs_itBecomesAFallbackAndIsNotCached() {
        RecommendationResult onlyKnown = RecommendationResult.ok(List.of(
                new RecomendacionItem("Bohemian Rhapsody", "Queen", "rock", "x")), "OLLAMA", "v2");
        providerAnswers(CompletableFuture.completedFuture(onlyKnown));

        RecommendationResponse first = service.recomendar("Rock", OWNER_A);
        service.recomendar("Rock", OWNER_A);

        assertThat(first.fallback()).isTrue();
        assertThat(first.motivoFallback()).isEqualTo("INVALID_RESPONSE");
        assertThat(providerCalls).hasValue(2);
    }

    @Test
    void fallbackSuggestions_neverIncludeASongTheUserAlreadyHas() {
        // The curated rock catalog contains Bohemian Rhapsody, which this playlist already has
        RecommendationResult catalogFallback = RecommendationResult.fallback(
                new FallbackCatalog().suggestionsFor("rock"), "TIMEOUT", "v2");
        providerAnswers(CompletableFuture.completedFuture(catalogFallback));

        RecommendationResponse response = service.recomendar("Rock", OWNER_A);

        assertThat(response.fallback()).isTrue();
        assertThat(response.recomendaciones()).extracting(RecomendacionItem::titulo)
                .doesNotContain("Bohemian Rhapsody").isNotEmpty();
    }
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
