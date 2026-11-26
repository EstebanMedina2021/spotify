package com.playlist.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(2) // después del CsrfFilter de Spring Security
@RequiredArgsConstructor
public class CsrfValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/auth/login", "/auth/logout", "/auth/me", "/auth/csrf"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Métodos seguros: no requieren CSRF
        if (SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Rutas exentas: no requieren CSRF
        if (EXEMPT_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // Para métodos no seguros: validar header contra cookie
        String cookieToken = getCookieValue(request, "XSRF-TOKEN");
        String headerToken = request.getHeader("X-XSRF-TOKEN");

        if (cookieToken == null || headerToken == null || !cookieToken.equals(headerToken)) {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    "CSRF_INVALID",
                    "Token CSRF ausente o inválido",
                    Instant.now(),
                    request.getRequestURI()
            );
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        chain.doFilter(request, response);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}