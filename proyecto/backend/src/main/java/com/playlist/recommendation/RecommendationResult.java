package com.playlist.recommendation;

import java.util.List;

public record RecommendationResult(
        List<RecomendacionItem> recomendaciones,
        String fuente,
        String motivoFallback,
        boolean fallback,
        String promptVersion
) {

    public static RecommendationResult ok(List<RecomendacionItem> items, String fuente, String promptVersion) {
        return new RecommendationResult(items, fuente, null, false, promptVersion);
    }

    public static RecommendationResult fallback(List<RecomendacionItem> items, String motivo, String promptVersion) {
        return new RecommendationResult(items, "FALLBACK", motivo, true, promptVersion);
    }

    public static RecommendationResult listaVacia(String promptVersion) {
        return new RecommendationResult(List.of(), null, null, false, promptVersion);
    }
}