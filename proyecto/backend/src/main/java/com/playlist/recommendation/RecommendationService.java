package com.playlist.recommendation;

import com.github.benmanes.caffeine.cache.Cache;
import com.playlist.playlist.PlaylistService;
import com.playlist.playlist.dto.PlaylistResponse;
import com.playlist.recommendation.dto.RecommendationResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Not transactional on purpose: the playlist is read in the short transaction of
 * {@link PlaylistService#obtener}, which ends before the (slow) AI call starts, so no database
 * connection is held while waiting for a third party.
 */
@Slf4j
@Service
public class RecommendationService {

    private final PlaylistService playlistService;
    private final AiRecommendationClient aiRecommendationClient;
    private final PromptTemplates promptTemplates;
    private final FallbackCatalog fallbackCatalog;
    private final Cache<String, RecommendationResult> recommendationCache;
    private final String aiProvider;

    public RecommendationService(
            PlaylistService playlistService,
            AiRecommendationClient aiRecommendationClient,
            PromptTemplates promptTemplates,
            FallbackCatalog fallbackCatalog,
            Cache<String, RecommendationResult> recommendationCache,
            @Value("${app.ai.provider:mock}") String aiProvider) {
        this.playlistService = playlistService;
        this.aiRecommendationClient = aiRecommendationClient;
        this.promptTemplates = promptTemplates;
        this.fallbackCatalog = fallbackCatalog;
        this.recommendationCache = recommendationCache;
        this.aiProvider = aiProvider;
    }

    public RecommendationResponse recomendar(String playlistNombre, UUID ownerId) {
        // Throws PlaylistNotFoundException (404) when it does not exist or belongs to someone else.
        PlaylistResponse playlist = playlistService.obtener(playlistNombre, ownerId);

        if (playlist.canciones().isEmpty()) {
            return RecommendationResponse.fromResult(
                    playlist.nombre(),
                    List.of(),
                    null,
                    promptTemplates.getPromptVersion(),
                    false,
                    null,
                    "La lista no contiene canciones para generar recomendaciones."
            );
        }

        List<CancionSnapshot> canciones = playlist.canciones().stream()
                .map(c -> new CancionSnapshot(c.titulo(), c.artista(), c.genero()))
                .toList();

        try {
            RecommendationResult result = resolve(buildCacheKey(ownerId, playlist.id(), canciones), canciones);
            String mensaje = result.fallback() ? buildFallbackMessage(result.motivoFallback()) : null;
            return toResponse(playlist.nombre(), result, mensaje);
        } catch (Exception ex) {
            // Defense in depth: the decorator never fails, but a bug or a swapped client must not become a 500.
            String motivo = AiFailureClassifier.motivoFor(ex);
            log.warn("AI client failed: motivo={}, cause={}", motivo, ex.getClass().getSimpleName());

            RecommendationResult fallbackResult = RecommendationResult.fallback(
                    fallbackCatalog.suggestionsFor(fallbackCatalog.firstGenre(canciones)),
                    motivo,
                    promptTemplates.getPromptVersion()
            );
            return toResponse(playlist.nombre(), fallbackResult, buildFallbackMessage(motivo));
        }
    }

    /**
     * Cache lookup and provider call in one atomic step per key: identical concurrent requests
     * wait for a single provider call instead of each paying for one. Fallbacks are returned
     * but never stored (a null from the loader means "do not cache").
     */
    private RecommendationResult resolve(String cacheKey, List<CancionSnapshot> canciones) {
        AtomicReference<RecommendationResult> notCached = new AtomicReference<>();

        RecommendationResult cached = recommendationCache.get(cacheKey, key -> {
            RecommendationResult fresh = aiRecommendationClient
                    .recommend(canciones, promptTemplates.getPromptVersion())
                    .join();
            if (fresh.fallback()) {
                notCached.set(fresh);
                return null;
            }
            return fresh;
        });

        return cached != null ? cached : notCached.get();
    }

    private RecommendationResponse toResponse(String playlistNombre, RecommendationResult result, String mensaje) {
        return RecommendationResponse.fromResult(
                playlistNombre,
                result.recomendaciones(),
                result.fuente(),
                result.promptVersion(),
                result.fallback(),
                result.motivoFallback(),
                mensaje
        );
    }

    private String buildFallbackMessage(String motivo) {
        if (motivo == null) {
            return null;
        }
        return switch (motivo) {
            case "TIMEOUT" -> "El proveedor de IA no respondió a tiempo. Se muestran sugerencias genéricas.";
            case "PROVIDER_AUTH_ERROR" -> "Configuración del proveedor de IA inválida. Se muestran sugerencias genéricas.";
            case "INVALID_RESPONSE" -> "La IA devolvió un formato inválido. Se muestran sugerencias genéricas.";
            case "CIRCUIT_OPEN" -> "El proveedor de IA está temporalmente deshabilitado. Se muestran sugerencias genéricas.";
            case "PROVIDER_DOWN" -> "El proveedor de IA no está disponible. Se muestran sugerencias genéricas.";
            default -> "Se muestran sugerencias genéricas.";
        };
    }

    private String buildCacheKey(UUID ownerId, UUID playlistId, List<CancionSnapshot> canciones) {
        String hash = sha256(canciones.stream()
                .map(c -> (c.titulo() == null ? "" : c.titulo())
                        + "|" + (c.artista() == null ? "" : c.artista())
                        + "|" + (c.genero() == null ? "" : c.genero()))
                .collect(Collectors.joining(";")));
        return ownerId + ":" + playlistId + ":" + hash
                + ":" + promptTemplates.getPromptVersion()
                + ":" + aiProvider;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
