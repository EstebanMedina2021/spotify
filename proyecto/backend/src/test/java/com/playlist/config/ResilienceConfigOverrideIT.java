package com.playlist.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Proves the settings are really configuration: changing the properties changes the beans. */
@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.instances.aiClient.sliding-window-size=7",
        "app.ai.timeout-seconds=2"
})
@ActiveProfiles("test")
class ResilienceConfigOverrideIT {

    @Autowired private CircuitBreaker aiCircuitBreaker;
    @Autowired private TimeLimiter aiTimeLimiter;

    @Test
    void overriddenProperties_reachTheBeans() {
        assertThat(aiCircuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(7);
        assertThat(aiTimeLimiter.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(2));
    }
}
