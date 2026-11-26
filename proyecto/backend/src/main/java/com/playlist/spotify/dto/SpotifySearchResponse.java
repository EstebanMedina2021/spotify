package com.playlist.spotify.dto;

import java.util.List;

public record SpotifySearchResponse(List<SpotifyTrackResult> resultados) {
}
