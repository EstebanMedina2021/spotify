package com.playlist.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.config.SpotifyConfig;
import com.playlist.spotify.exception.SpotifyUnavailableException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Client Credentials flow. The token is cached (max 55 min) and renewed on demand. */
@Service
public class SpotifyTokenService {

    static final Duration MAX_TTL = Duration.ofMinutes(55);
    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(60);

    private final SpotifyProperties properties;
    private final RestClient accountsClient;
    private final ObjectMapper objectMapper;

    private volatile CachedToken cached;

    public SpotifyTokenService(SpotifyProperties properties,
                               @Qualifier(SpotifyConfig.ACCOUNTS_CLIENT) RestClient accountsClient,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.accountsClient = accountsClient;
        this.objectMapper = objectMapper;
    }

    public String getToken() {
        CachedToken current = cached;
        if (current != null && current.isValid()) {
            return current.value();
        }
        return refresh();
    }

    /** Drops the cached token, e.g. after Spotify answered 401. */
    public void invalidate() {
        cached = null;
    }

    private synchronized String refresh() {
        CachedToken current = cached;
        if (current != null && current.isValid()) {
            return current.value();
        }
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            throw new SpotifyUnavailableException("Spotify credentials are not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        try {
            String body = accountsClient.post()
                    .uri("/api/token")
                    .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(body);
            String token = node.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new SpotifyUnavailableException("Spotify token response without access_token");
            }
            Duration ttl = Duration.ofSeconds(node.path("expires_in").asLong(MAX_TTL.toSeconds()))
                    .minus(SAFETY_MARGIN);
            if (ttl.compareTo(MAX_TTL) > 0) {
                ttl = MAX_TTL;
            }
            if (ttl.isNegative() || ttl.isZero()) {
                ttl = Duration.ofSeconds(30);
            }
            cached = new CachedToken(token, Instant.now().plus(ttl));
            return token;
        } catch (SpotifyUnavailableException ex) {
            throw ex;
        } catch (RestClientException | java.io.IOException ex) {
            // Never include the response body or credentials in the message.
            throw new SpotifyUnavailableException("Could not obtain Spotify token", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
