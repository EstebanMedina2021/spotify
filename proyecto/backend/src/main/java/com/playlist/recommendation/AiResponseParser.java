package com.playlist.recommendation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Parses and validates the raw LLM output. The response is untrusted: it is only
 * parsed, never evaluated, and every item is checked against the expected schema.
 */
@Component
public class AiResponseParser {

    static final int MAX_RECOMMENDATIONS = 5;
    static final int MAX_REASON_LENGTH = 200;

    private final ObjectReader reader;

    public AiResponseParser(ObjectMapper objectMapper) {
        // Unknown fields are ignored: LLMs often add extra keys.
        this.reader = objectMapper.readerFor(AiRawResponse.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public RecommendationResult parse(String raw, String fuente, String promptVersion) {
        if (raw == null || raw.isBlank()) {
            throw new AiInvalidResponseException("Respuesta vacía del proveedor");
        }

        AiRawResponse parsed;
        try {
            parsed = reader.readValue(stripCodeFence(raw));
        } catch (Exception ex) {
            // Do not echo the raw payload or parser message: it may contain model output.
            throw new AiInvalidResponseException("No se pudo parsear la respuesta del LLM", ex);
        }

        if (parsed == null || parsed.recomendaciones() == null) {
            throw new AiInvalidResponseException("Respuesta sin campo 'recomendaciones'");
        }

        List<RecomendacionItem> valid = parsed.recomendaciones().stream()
                .filter(r -> r != null)
                .filter(r -> r.titulo() != null && !r.titulo().isBlank())
                .filter(r -> r.artista() != null && !r.artista().isBlank())
                .map(AiResponseParser::truncateReason)
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        if (valid.isEmpty()) {
            throw new AiInvalidResponseException("Ninguna recomendación válida en la respuesta");
        }
        return RecommendationResult.ok(valid, fuente, promptVersion);
    }

    private static String stripCodeFence(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return json;
    }

    private static RecomendacionItem truncateReason(RecomendacionItem item) {
        String razon = item.razon();
        if (razon != null && razon.length() > MAX_REASON_LENGTH) {
            razon = razon.substring(0, MAX_REASON_LENGTH);
        }
        return new RecomendacionItem(item.titulo(), item.artista(), item.genero(), razon);
    }

    private record AiRawResponse(List<RecomendacionItem> recomendaciones) {
    }
}
