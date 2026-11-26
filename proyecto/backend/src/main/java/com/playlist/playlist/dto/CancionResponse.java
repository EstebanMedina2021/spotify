package com.playlist.playlist.dto;

import java.util.UUID;

public record CancionResponse(
        UUID id,
        String titulo,
        String artista,
        String album,
        String anno,
        String genero
) {
}