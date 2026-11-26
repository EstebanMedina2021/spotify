package com.playlist.common.exception;

public class DuplicatePlaylistException extends RuntimeException {
    public DuplicatePlaylistException(String nombre) {
        super("Ya existe una lista con el nombre '" + nombre + "'");
    }
}