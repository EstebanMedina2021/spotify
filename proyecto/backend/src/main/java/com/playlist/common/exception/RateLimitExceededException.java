package com.playlist.common.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Demasiadas solicitudes");
    }
}