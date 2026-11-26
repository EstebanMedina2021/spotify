package com.playlist.recommendation.exception;

public class AiProviderAuthException extends RuntimeException {
    public AiProviderAuthException(String message) {
        super(message);
    }
    public AiProviderAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}