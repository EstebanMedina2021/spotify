package com.playlist.spotify;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.playlist.spotify.dto.SpotifyGenresResponse;
import com.playlist.spotify.dto.SpotifySearchResponse;
import com.playlist.spotify.exception.SpotifyEndpointUnavailableException;
import com.playlist.spotify.exception.SpotifyRateLimitedException;
import com.playlist.spotify.exception.SpotifyUnavailableException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SpotifyService {

    static final String REASON_ENDPOINT_UNAVAILABLE = "ENDPOINT_UNAVAILABLE";
    static final String REASON_PROVIDER_DOWN = "PROVIDER_DOWN";
    private static final String GENRES_KEY = "genres";

    private final SpotifyClient spotifyClient;
    private final GenreCatalog genreCatalog;

    // TTL 1 h (DECISIONES-FINALES §3.2). Only stable results are cached, never transient failures.
    private final Cache<String, SpotifyGenresResponse> genresCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public SpotifyService(SpotifyClient spotifyClient, GenreCatalog genreCatalog) {
        this.spotifyClient = spotifyClient;
        this.genreCatalog = genreCatalog;
    }

    public SpotifyGenresResponse listGenres() {
        SpotifyGenresResponse cached = genresCache.getIfPresent(GENRES_KEY);
        if (cached != null) {
            return cached;
        }
        try {
            var genres = spotifyClient.availableGenreSeeds();
            if (genres.isEmpty()) {
                return curated(REASON_ENDPOINT_UNAVAILABLE, true);
            }
            SpotifyGenresResponse response = new SpotifyGenresResponse(genres, false, null);
            genresCache.put(GENRES_KEY, response);
            return response;
        } catch (SpotifyEndpointUnavailableException ex) {
            log.info("Spotify genre endpoint not available for this app, using curated list");
            return curated(REASON_ENDPOINT_UNAVAILABLE, true);
        } catch (SpotifyUnavailableException | SpotifyRateLimitedException ex) {
            log.warn("Spotify genres failed ({}), using curated list", ex.getClass().getSimpleName());
            return curated(REASON_PROVIDER_DOWN, false);
        }
    }

    public SpotifySearchResponse search(String query, int limit) {
        return new SpotifySearchResponse(spotifyClient.searchTracks(query.trim(), limit));
    }

    /** Test hook. */
    void clearCache() {
        genresCache.invalidateAll();
    }

    private SpotifyGenresResponse curated(String reason, boolean cache) {
        SpotifyGenresResponse response = new SpotifyGenresResponse(genreCatalog.genres(), true, reason);
        if (cache) {
            genresCache.put(GENRES_KEY, response);
        }
        return response;
    }
}
