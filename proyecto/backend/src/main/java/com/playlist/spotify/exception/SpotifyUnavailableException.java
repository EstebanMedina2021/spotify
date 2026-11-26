package com.playlist.spotify.exception;

/** Spotify could not be reached or answered with an unexpected error. */
public class SpotifyUnavailableException extends RuntimeException {

    public SpotifyUnavailableException(String message) {
        super(message);
    }

    public SpotifyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
