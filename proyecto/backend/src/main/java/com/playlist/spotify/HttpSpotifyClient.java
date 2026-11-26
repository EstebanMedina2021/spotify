package com.playlist.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.config.SpotifyConfig;
import com.playlist.spotify.dto.SpotifyTrackResult;
import com.playlist.spotify.exception.SpotifyEndpointUnavailableException;
import com.playlist.spotify.exception.SpotifyRateLimitedException;
import com.playlist.spotify.exception.SpotifyUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

@Component
public class HttpSpotifyClient implements SpotifyClient {

    private static final long DEFAULT_RETRY_AFTER_SECONDS = 60;

    private final RestClient apiClient;
    private final SpotifyTokenService tokenService;
    private final ObjectMapper objectMapper;

    public HttpSpotifyClient(@Qualifier(SpotifyConfig.API_CLIENT) RestClient apiClient,
                             SpotifyTokenService tokenService,
                             ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SpotifyTrackResult> searchTracks(String query, int limit) {
        // Template variables are encoded by the UriBuilder, so q cannot inject extra parameters.
        String json = get(b -> b.path("/search")
                .queryParam("q", "{q}")
                .queryParam("type", "track")
                .queryParam("limit", "{limit}")
                .build(Map.of("q", query, "limit", limit)));

        List<SpotifyTrackResult> results = new ArrayList<>();
        for (JsonNode item : parse(json).path("tracks").path("items")) {
            String id = item.path("id").asText(null);
            String name = item.path("name").asText(null);
            if (id == null || name == null) {
                continue;
            }
            List<String> artists = new ArrayList<>();
            item.path("artists").forEach(a -> artists.add(a.path("name").asText("")));
            results.add(new SpotifyTrackResult(
                    name,
                    String.join(", ", artists),
                    item.path("album").path("name").asText(null),
                    id));
        }
        return results;
    }

    @Override
    public List<String> availableGenreSeeds() {
        String json = get(b -> b.path("/recommendations/available-genre-seeds").build());
        List<String> genres = new ArrayList<>();
        parse(json).path("genres").forEach(g -> genres.add(g.asText()));
        return genres;
    }

    /** GET with bearer token; on 401 the token is renewed once and the call retried. */
    private String get(Function<UriBuilder, URI> uri) {
        for (int attempt = 0; ; attempt++) {
            String token = tokenService.getToken();
            try {
                return apiClient.get()
                        .uri(uri)
                        .headers(h -> h.setBearerAuth(token))
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401 && attempt == 0) {
                    tokenService.invalidate();
                    continue;
                }
                throw map(status, ex);
            } catch (RestClientException ex) {
                // Timeouts, connection errors, etc.
                throw new SpotifyUnavailableException("Spotify request failed", ex);
            }
        }
    }

    private RuntimeException map(int status, RestClientResponseException ex) {
        if (status == 429) {
            return new SpotifyRateLimitedException(retryAfter(ex));
        }
        if (status == 403 || status == 404) {
            return new SpotifyEndpointUnavailableException("Spotify endpoint unavailable: HTTP " + status, ex);
        }
        return new SpotifyUnavailableException("Spotify error: HTTP " + status, ex);
    }

    private long retryAfter(RestClientResponseException ex) {
        String header = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
        if (header != null) {
            try {
                long seconds = Long.parseLong(header.trim());
                if (seconds > 0) {
                    return seconds;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_RETRY_AFTER_SECONDS;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException | RuntimeException ex) {
            throw new SpotifyUnavailableException("Invalid Spotify response", ex);
        }
    }
}
