package com.playlist.common;

public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String CSRF_INVALID = "CSRF_INVALID";
    public static final String PLAYLIST_NOT_FOUND = "PLAYLIST_NOT_FOUND";
    public static final String PLAYLIST_DUPLICATED = "PLAYLIST_DUPLICATED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SPOTIFY_RATE_LIMITED = "SPOTIFY_RATE_LIMITED";
    public static final String SPOTIFY_UNAVAILABLE = "SPOTIFY_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}