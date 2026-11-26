package com.playlist.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.common.ErrorCodes;
import com.playlist.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Double-submit CSRF check (cookie {@code XSRF-TOKEN} must equal header {@code X-XSRF-TOKEN}) for
 * every state-changing request.
 *
 * <p><b>This filter is the effective CSRF protection; do not remove it as "redundant".</b> The
 * OAuth2 resource server automatically exempts from Spring's own {@code CsrfFilter} every request
 * for which its token resolver finds a token. Our resolver reads the {@code access_token} cookie,
 * so every authenticated request is exempted and, without this filter, {@code POST /lists} succeeds
 * with no CSRF token at all (verified by {@code SecurityIT}).
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class CsrfValidationFilter extends OncePerRequestFilter {

    /** Paths that never need a CSRF token. Shared with SecurityConfig so both lists cannot drift. */
    public static final String[] EXEMPT_PATH_PATTERNS = {"/auth/login", "/auth/logout", "/auth/me"};

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> EXEMPT_PATHS = Set.of(EXEMPT_PATH_PATTERNS);

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (SAFE_METHODS.contains(request.getMethod()) || EXEMPT_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        if (!hasValidToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    ErrorCodes.CSRF_INVALID,
                    "Token CSRF ausente o inválido",
                    Instant.now(),
                    request.getRequestURI()
            );
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String cookieToken = getCookieValue(request, "XSRF-TOKEN");
        String headerToken = request.getHeader("X-XSRF-TOKEN");
        if (cookieToken == null || headerToken == null || cookieToken.isBlank() || headerToken.isBlank()) {
            return false;
        }
        // Constant-time comparison
        return MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
