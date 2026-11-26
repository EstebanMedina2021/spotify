package com.playlist.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.playlist.common.ErrorCodes;
import com.playlist.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limit.login-per-ip:5}")
    private int loginPerIp;

    @Value("${app.rate-limit.login-per-user:5}")
    private int loginPerUser;

    @Value("${app.rate-limit.lists:60}")
    private int listsLimit;

    @Value("${app.rate-limit.recommendations:5}")
    private int recommendationsLimit;

    @Value("${app.rate-limit.spotify:30}")
    private int spotifyLimit;

    @Bean
    public ConcurrentMap<String, Bucket> rateLimitBuckets() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .<String, Bucket>build()
                .asMap();
    }

    @Bean
    public RateLimitFilter rateLimitFilter(ConcurrentMap<String, Bucket> buckets,
                                           ObjectMapper objectMapper) {
        return new RateLimitFilter(buckets, objectMapper, loginPerIp, loginPerUser);
    }

    @RequiredArgsConstructor
    public static class RateLimitFilter extends OncePerRequestFilter {

        private final ConcurrentMap<String, Bucket> buckets;
        private final ObjectMapper objectMapper;
        private final int loginPerIp;
        private final int loginPerUser;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String path = request.getRequestURI();
            String method = request.getMethod();

            if ("POST".equals(method) && "/auth/login".equals(path)) {
                String ip = request.getRemoteAddr();
                if (!tryConsume("login-ip:" + ip, loginPerIp, response, request)) {
                    return;
                }
            }

            // NUEVO: rate limit para recomendaciones
            if ("GET".equals(method) && path.matches("^/lists/[^/]+/recommendations$")) {
                String userId = extractUserIdFromSecurityContext();
                if (userId != null) {
                    if (!tryConsume("recommendations:" + userId, 5, response, request)) {
                        return;
                    }
                }
            }

            filterChain.doFilter(request, response);
        }

        private String extractUserIdFromSecurityContext() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return jwt.getSubject();
            }
            return null;
        }

        private boolean tryConsume(String key, int capacity, HttpServletResponse response,
                                   HttpServletRequest request) throws IOException {
            Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(capacity));
            if (bucket.tryConsume(1)) {
                return true;
            }
            long waitSeconds = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill() / 1_000_000_000L;
            if (waitSeconds < 1) {
                waitSeconds = 1;
            }
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    ErrorCodes.RATE_LIMITED,
                    "Demasiadas solicitudes",
                    Instant.now(),
                    request.getRequestURI()
            );
            objectMapper.writeValue(response.getOutputStream(), body);
            return false;
        }

        private Bucket createBucket(int capacity) {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(capacity, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(limit).build();
        }
    }
}