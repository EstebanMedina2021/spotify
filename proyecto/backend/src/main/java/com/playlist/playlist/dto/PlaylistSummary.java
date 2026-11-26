package com.playlist.playlist.dto;

import java.util.UUID;

public record PlaylistSummary(
        UUID id,
        String nombre,
        String descripcion,
        int numeroCanciones
) {
}