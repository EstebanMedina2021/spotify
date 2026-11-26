package com.playlist.auth.dto;

public record LoginResponse(
        String username,
        String expiresAt
) {
}