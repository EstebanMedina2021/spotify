package com.playlist.common.exception;

/** Text input (name, path variable...) contains characters that are never accepted, e.g. control characters. */
public class InvalidTextException extends IllegalArgumentException {

    public InvalidTextException(String message) {
        super(message);
    }
}
