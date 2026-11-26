package com.playlist.common.exception;

public class PlaylistNotFoundException extends RuntimeException {
    public PlaylistNotFoundException(String nombre) {
        super("La lista '" + nombre + "' no existe");
    }
}