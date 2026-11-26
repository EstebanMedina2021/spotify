package com.playlist.spotify.dto;

public record SpotifyTrackResult(
        String titulo,
        String artista,
        String album,
        String spotifyId
) {
}
