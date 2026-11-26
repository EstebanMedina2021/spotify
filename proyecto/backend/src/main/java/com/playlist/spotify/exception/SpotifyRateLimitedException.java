package com.playlist.spotify.exception;

/** Spotify answered HTTP 429. Carries the Retry-After value to propagate to the caller. */
public class SpotifyRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public SpotifyRateLimitedException(long retryAfterSeconds) {
        super("Spotify rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
