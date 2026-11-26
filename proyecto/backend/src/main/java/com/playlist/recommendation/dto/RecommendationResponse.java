package com.playlist.recommendation.dto;

import com.playlist.recommendation.RecomendacionItem;
import java.util.List;

public record RecommendationResponse(
        String playlistNombre,
        List<RecomendacionItem> recomendaciones,
        String fuente,
        String promptVersion,
        boolean fallback,
        String motivoFallback,
        String mensaje
) {

    public static RecommendationResponse fromResult(
            String playlistNombre,
            List<RecomendacionItem> recomendaciones,
            String fuente,
            String promptVersion,
            boolean fallback,
            String motivoFallback,
            String mensaje) {
        return new RecommendationResponse(
                playlistNombre,
                recomendaciones,
                fuente,
                promptVersion,
                fallback,
                motivoFallback,
                mensaje
        );
    }
}