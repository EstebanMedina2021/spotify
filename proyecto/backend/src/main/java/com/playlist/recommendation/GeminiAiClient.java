package com.playlist.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiAiClient implements AiRecommendationClient {

    private final ChatClient chatClient;
    private final PromptTemplates promptTemplates;
    private final ObjectMapper objectMapper;

    public GeminiAiClient(ChatClient.Builder chatClientBuilder,
                          PromptTemplates promptTemplates,
                          ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.promptTemplates = promptTemplates;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<RecommendationResult> recommend(List<CancionSnapshot> canciones, String promptVersion) {
        return CompletableFuture.supplyAsync(() -> {
            String prompt = promptTemplates.buildPrompt(canciones);
            String rawResponse;
            try {
                rawResponse = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
            } catch (Exception ex) {
                throw mapException(ex);
            }

            log.debug("Gemini raw response: {}", rawResponse);
            return parseAndValidate(rawResponse, "GEMINI", promptVersion);
        });
    }

    private RecommendationResult parseAndValidate(String raw, String fuente, String promptVersion) {
        try {
            // El LLM puede devolver bloques ```json ... ```, los limpiamos
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            AiRawResponse parsed = objectMapper.readValue(json, AiRawResponse.class);
            if (parsed == null || parsed.recomendaciones() == null) {
                throw new AiInvalidResponseException("Respuesta sin campo 'recomendaciones'");
            }

            List<RecomendacionItem> valid = parsed.recomendaciones().stream()
                    .filter(r -> r.titulo() != null && !r.titulo().isBlank())
                    .filter(r -> r.artista() != null && !r.artista().isBlank())
                    .limit(5)
                    .toList();

            if (valid.isEmpty()) {
                throw new AiInvalidResponseException("Ninguna recomendación válida en la respuesta");
            }

            return RecommendationResult.ok(valid, fuente, promptVersion);
        } catch (AiInvalidResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiInvalidResponseException("No se pudo parsear la respuesta del LLM: " + ex.getMessage(), ex);
        }
    }

    private RuntimeException mapException(Exception ex) {
        if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
            return new AiProviderTimeoutException("Timeout llamando a Gemini", ex);
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")
                || msg.contains("api key") || msg.contains("permission")) {
            return new AiProviderAuthException("Error de autenticación con Gemini", ex);
        }
        return new AiProviderException("Error llamando a Gemini", ex);
    }

    private record AiRawResponse(List<RecomendacionItem> recomendaciones) {
    }
}