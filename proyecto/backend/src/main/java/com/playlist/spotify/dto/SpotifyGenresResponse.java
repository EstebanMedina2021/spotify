package com.playlist.spotify.dto;

import java.util.List;

public record SpotifyGenresResponse(
        List<String> generos,
        boolean fallback,
        String motivoFallback
) {
}
