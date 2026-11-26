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

    @Value("${app.rate-limit.logout:30}")
    private int logoutLimit;

    @Value("${app.rate-limit.me:60}")
    private int meLimit;

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
        return new RateLimitFilter(buckets, objectMapper, loginPerIp, loginPerUser,
                recommendationsLimit, spotifyLimit, listsLimit, logoutLimit, meLimit);
    }

    @RequiredArgsConstructor
    public static class RateLimitFilter extends OncePerRequestFilter {

        private final ConcurrentMap<String, Bucket> buckets;
        private final ObjectMapper objectMapper;
        private final int loginPerIp;
        private final int loginPerUser;
        private final int recommendationsLimit;
        private final int spotifyLimit;
        private final int listsLimit;
        private final int logoutLimit;
        private final int meLimit;

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

            if ("GET".equals(method) && path.matches("^/lists/[^/]+/recommendations$")) {
                String userId = extractUserIdFromSecurityContext();
                if (userId != null
                        && !tryConsume("recommendations:" + userId, recommendationsLimit, response, request)) {
                    return;
                }
            }

            if ("POST".equals(method) && "/auth/logout".equals(path)
                    && !tryConsume("logout:" + userOrIp(request), logoutLimit, response, request)) {
                return;
            }

            if ("GET".equals(method) && "/auth/me".equals(path)
                    && !tryConsume("me:" + userOrIp(request), meLimit, response, request)) {
                return;
            }

            // All /lists/** (except recommendations, which has its own stricter bucket) share one bucket per user
            if ((path.equals("/lists") || path.startsWith("/lists/"))
                    && !path.endsWith("/recommendations")) {
                String userId = extractUserIdFromSecurityContext();
                if (userId != null && !tryConsume("lists:" + userId, listsLimit, response, request)) {
                    return;
                }
            }

            // /spotify/genres and /spotify/search share one bucket per user
            if (path.startsWith("/spotify/")) {
                String userId = extractUserIdFromSecurityContext();
                if (userId != null && !tryConsume("spotify:" + userId, spotifyLimit, response, request)) {
                    return;
                }
            }

            filterChain.doFilter(request, response);
        }

        /** Authenticated user id, or the remote address when the request is anonymous. */
        private String userOrIp(HttpServletRequest request) {
            String userId = extractUserIdFromSecurityContext();
            return userId != null ? userId : "ip:" + request.getRemoteAddr();
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