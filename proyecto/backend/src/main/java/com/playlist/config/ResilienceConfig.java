package com.playlist.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService aiTimeoutScheduler() {
        return Executors.newScheduledThreadPool(3);
    }

    @Bean
    public TimeLimiter aiTimeLimiter(
            @Value("${app.ai.timeout-seconds:5}") long timeoutSeconds) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(timeoutSeconds))
                .cancelRunningFuture(true)
                .build();
        return TimeLimiterRegistry.of(config).timeLimiter("aiClient");
    }

    @Bean
    public CircuitBreaker aiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreakerRegistry.of(config).circuitBreaker("aiClient");
    }
}