package com.playlist.playlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistResponse(
        UUID id,
        String nombre,
        String descripcion,
        List<CancionResponse> canciones,
        Instant createdAt,
        Instant updatedAt
) {
}