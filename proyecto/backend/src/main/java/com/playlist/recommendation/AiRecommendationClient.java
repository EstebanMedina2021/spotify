package com.playlist.recommendation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AiRecommendationClient {

    /**
     * Genera recomendaciones a partir de las canciones de la lista.
     *
     * @param canciones canciones de la lista, ya truncadas a 20 o 2000 chars por PromptTemplates.
     * @param promptVersion versión del prompt (v1).
     * @return CompletableFuture con el resultado. Nunca completa excepcionalmente:
     *         el FallbackAiClient captura las excepciones y devuelve un resultado con fallback=true.
     */
    CompletableFuture<RecommendationResult> recommend(List<CancionSnapshot> canciones, String promptVersion);
}