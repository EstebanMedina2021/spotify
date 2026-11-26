package com.playlist.auth;

import java.time.Instant;

/**
 * A freshly signed JWT with the exact expiry it carries in its {@code exp} claim (second
 * precision), so the value returned to the client matches what {@code /auth/me} reports later.
 */
public record IssuedToken(String value, Instant expiresAt) {
}
