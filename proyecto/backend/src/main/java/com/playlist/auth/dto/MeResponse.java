package com.playlist.auth.dto;

public record MeResponse(
        String username,
        String expiresAt
) {
}