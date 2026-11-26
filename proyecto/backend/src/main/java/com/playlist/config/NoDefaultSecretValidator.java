package com.playlist.config;

import com.playlist.auth.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoDefaultSecretValidator implements InitializingBean {

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Value("${app.ai.provider:mock}")
    private String aiProvider;

    @Value("${GEMINI_AI_API_KEY:}")
    private String geminiKey;

    @Value("${GEMINI_AI_MODEL:}")
    private String geminiModel;

    @Value("${OLLAMA_BASE_URL:}")
    private String ollamaBaseUrl;

    @Value("${SPOTIFY_CLIENT_ID:}")
    private String spotifyClientId;

    @Value("${SPOTIFY_CLIENT_SECRET:}")
    private String spotifyClientSecret;

    @Override
    public void afterPropertiesSet() {
        validateJwtSecret();
        validateAiSecrets();
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            validateSpotifySecrets();
        }
    }

    private void validateJwtSecret() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required and must not be empty");
        }
        if (looksLikeExamplePlaceholder(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET still holds the example placeholder; generate a real one with: openssl rand -base64 64");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length * 8 < 256) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 256 bits (32 bytes). Current length: "
                            + (keyBytes.length * 8) + " bits");
        }
    }

    /** Values copied from .env.example or documentation are public, so tokens signed with them are forgeable. */
    private boolean looksLikeExamplePlaceholder(String secret) {
        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("<")
                || normalized.contains("change-me")
                || normalized.contains("changeme")
                || normalized.contains("your-secret");
    }

    private void validateAiSecrets() {
        if ("gemini".equalsIgnoreCase(aiProvider)) {
            if (geminiKey == null || geminiKey.isBlank()) {
                throw new IllegalStateException("GEMINI_AI_API_KEY is required when AI_PROVIDER=gemini");
            }
            if (geminiModel == null || geminiModel.isBlank()) {
                // Model ids are retired regularly; a stale default would only fail later, at the first request.
                throw new IllegalStateException(
                        "GEMINI_AI_MODEL is required when AI_PROVIDER=gemini (use a current id from the Gemini API docs)");
            }
        }
        if ("ollama".equalsIgnoreCase(aiProvider)) {
            if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) {
                throw new IllegalStateException("OLLAMA_BASE_URL is required when AI_PROVIDER=ollama");
            }
        }
        // mock → no key required
    }

    private void validateSpotifySecrets() {
        if (spotifyClientId == null || spotifyClientId.isBlank()
                || spotifyClientSecret == null || spotifyClientSecret.isBlank()) {
            throw new IllegalStateException(
                    "SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET are required in prod profile");
        }
    }
}