package com.playlist.spotify;

import com.playlist.spotify.dto.SpotifyTrackResult;
import java.util.List;

/** Port to the Spotify Web API. Keeps the service independent of the HTTP implementation. */
public interface SpotifyClient {

    /**
     * Searches tracks.
     *
     * @throws com.playlist.spotify.exception.SpotifyRateLimitedException on HTTP 429
     * @throws com.playlist.spotify.exception.SpotifyUnavailableException on any other failure
     */
    List<SpotifyTrackResult> searchTracks(String query, int limit);

    /**
     * Lists the available genre seeds.
     *
     * @throws com.playlist.spotify.exception.SpotifyEndpointUnavailableException if the endpoint is
     *         not available for this app (403/404)
     * @throws com.playlist.spotify.exception.SpotifyUnavailableException on any other failure
     */
    List<String> availableGenreSeeds();
}
