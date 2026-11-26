package com.playlist.recommendation;

public record RecomendacionItem(
        String titulo,
        String artista,
        String genero,
        String razon
) {
}