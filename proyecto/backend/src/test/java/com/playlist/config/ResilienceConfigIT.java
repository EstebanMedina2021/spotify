package com.playlist.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The values in application.yml (resilience4j.*.instances.aiClient) must be the ones really in use. */
@SpringBootTest
@ActiveProfiles("test")
class ResilienceConfigIT {

    @Autowired private CircuitBreaker aiCircuitBreaker;
    @Autowired private TimeLimiter aiTimeLimiter;

    @Test
    void circuitBreaker_usesTheValuesFromApplicationYml() {
        CircuitBreakerConfig config = aiCircuitBreaker.getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(30).toMillis());
    }

    @Test
    void timeLimiter_usesTheSameTimeoutAsTheHttpClients() {
        assertThat(aiTimeLimiter.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(5));
        assertThat(aiTimeLimiter.getTimeLimiterConfig().shouldCancelRunningFuture()).isTrue();
    }
}
