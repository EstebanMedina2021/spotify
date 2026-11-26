package com.playlist.recommendation;

import com.playlist.common.exception.RateLimitExceededException;
import com.playlist.recommendation.dto.RecommendationResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lists")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ConcurrentMap<String, Bucket> rateLimitBuckets;

    @GetMapping("/{listName}/recommendations")
    public RecommendationResponse recomendar(
            @PathVariable String listName,
            Authentication authentication,
            HttpServletRequest request) {
        UUID ownerId = extractUserId(authentication);

        // Rate limit: 5 req/min por usuario
        String key = "recommendations:" + ownerId;
        Bucket bucket = rateLimitBuckets.computeIfAbsent(key, k -> createBucket());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException();
        }

        return recommendationService.recomendar(listName, ownerId);
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillGreedy(5, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("No autenticado");
        }
        return UUID.fromString(jwt.getSubject());
    }
}