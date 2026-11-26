package com.playlist.spotify.exception;

/**
 * The Spotify endpoint exists but is not available for this application (HTTP 403/404),
 * e.g. endpoints removed for new apps.
 */
public class SpotifyEndpointUnavailableException extends SpotifyUnavailableException {

    public SpotifyEndpointUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
