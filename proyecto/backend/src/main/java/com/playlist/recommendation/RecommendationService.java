package com.playlist.recommendation;

import com.github.benmanes.caffeine.cache.Cache;
import com.playlist.common.TextNormalizer;
import com.playlist.common.exception.PlaylistNotFoundException;
import com.playlist.playlist.Playlist;
import com.playlist.playlist.PlaylistRepository;
import com.playlist.recommendation.dto.RecommendationResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class RecommendationService {

    private final PlaylistRepository playlistRepository;
    private final AiRecommendationClient aiRecommendationClient;
    private final PromptTemplates promptTemplates;
    private final FallbackCatalog fallbackCatalog;
    private final Cache<String, RecommendationResult> recommendationCache;
    private final String aiProvider;

    public RecommendationService(
            PlaylistRepository playlistRepository,
            AiRecommendationClient aiRecommendationClient,
            PromptTemplates promptTemplates,
            FallbackCatalog fallbackCatalog,
            Cache<String, RecommendationResult> recommendationCache,
            @Value("${app.ai.provider:mock}") String aiProvider) {
        this.playlistRepository = playlistRepository;
        this.aiRecommendationClient = aiRecommendationClient;
        this.promptTemplates = promptTemplates;
        this.fallbackCatalog = fallbackCatalog;
        this.recommendationCache = recommendationCache;
        this.aiProvider = aiProvider;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recomendar(String playlistNombre, UUID ownerId) {
        String nombreNormalizado = TextNormalizer.normalize(playlistNombre);
        Playlist playlist = playlistRepository
                .findByOwnerIdAndNombreNormalizado(ownerId, nombreNormalizado)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistNombre));

        if (playlist.getCanciones() == null || playlist.getCanciones().isEmpty()) {
            return RecommendationResponse.fromResult(
                    playlist.getNombre(),
                    List.of(),
                    null,
                    promptTemplates.getPromptVersion(),
                    false,
                    null,
                    "La lista no contiene canciones para generar recomendaciones."
            );
        }

        List<CancionSnapshot> canciones = playlist.getCanciones().stream()
                .map(c -> new CancionSnapshot(c.getTitulo(), c.getArtista(), c.getGenero()))
                .toList();

        String cacheKey = buildCacheKey(ownerId, playlist, canciones);
        RecommendationResult cached = recommendationCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for playlist {}: motivoFallback={}", playlistNombre, cached.motivoFallback());
            return toResponse(playlist.getNombre(), cached, null);
        }

        try {
            RecommendationResult result = aiRecommendationClient
                    .recommend(canciones, promptTemplates.getPromptVersion())
                    .join();

            if (!result.fallback()) {
                recommendationCache.put(cacheKey, result);
            }

            String mensaje = result.fallback()
                    ? buildFallbackMessage(result.motivoFallback())
                    : null;

            return toResponse(playlist.getNombre(), result, mensaje);
        } catch (Exception ex) {
            // Defensa en profundidad: si el cliente IA falla, generamos fallback local.
            String motivo = AiFailureClassifier.motivoFor(ex);
            log.warn("AI client failed: motivo={}, cause={}", motivo, ex.getClass().getSimpleName());

            RecommendationResult fallbackResult = RecommendationResult.fallback(
                    fallbackCatalog.suggestionsFor(fallbackCatalog.firstGenre(canciones)),
                    motivo,
                    promptTemplates.getPromptVersion()
            );
            return toResponse(playlist.getNombre(), fallbackResult,
                    buildFallbackMessage(motivo));
        }
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

    private String buildCacheKey(UUID ownerId, Playlist playlist, List<CancionSnapshot> canciones) {
        String hash = sha256(canciones.stream()
                .map(c -> (c.titulo() == null ? "" : c.titulo())
                        + "|" + (c.artista() == null ? "" : c.artista())
                        + "|" + (c.genero() == null ? "" : c.genero()))
                .collect(Collectors.joining(";")));
        return ownerId + ":" + playlist.getId() + ":" + hash
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