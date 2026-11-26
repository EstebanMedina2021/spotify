package com.playlist.recommendation;

import com.playlist.recommendation.dto.RecommendationResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rate limiting for this endpoint is applied by RateLimitFilter (5 req/min per user). */
@RestController
@RequestMapping("/lists")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{listName}/recommendations")
    public RecommendationResponse recomendar(
            @PathVariable String listName,
            Authentication authentication) {
        return recommendationService.recomendar(listName, extractUserId(authentication));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("No autenticado");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
