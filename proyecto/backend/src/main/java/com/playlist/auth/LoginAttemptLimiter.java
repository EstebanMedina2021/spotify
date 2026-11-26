package com.playlist.auth;

import com.playlist.common.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-username login throttle (03 §4.5: IP + username dual buckets). The per-IP bucket lives in
 * RateLimitFilter; this one needs the username, which is only available once the body is parsed.
 * Failed and successful attempts both count, so it also slows credential stuffing.
 */
@Component
public class LoginAttemptLimiter {

    private final ConcurrentMap<String, Bucket> buckets;
    private final int perMinute;

    public LoginAttemptLimiter(ConcurrentMap<String, Bucket> rateLimitBuckets,
                               @Value("${app.rate-limit.login-per-user:5}") int perMinute) {
        this.buckets = rateLimitBuckets;
        this.perMinute = perMinute;
    }

    public void check(String username) {
        String key = "login-user:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(perMinute)
                        .refillGreedy(perMinute, Duration.ofMinutes(1))
                        .build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException();
        }
    }
}
