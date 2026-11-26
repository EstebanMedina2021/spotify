package com.playlist.common;

import java.time.Instant;

public record ErrorResponse(
        String codigo,
        String mensaje,
        Instant timestamp,
        String path
) {
}