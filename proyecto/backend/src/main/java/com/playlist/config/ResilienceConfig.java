package com.playlist.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The AI client's resilience settings live in application.yml under
 * {@code resilience4j.*.instances.aiClient}; the Resilience4j starter builds the registries from
 * there and these beans just pick the "aiClient" instance. Nothing is hardcoded here.
 */
@Configuration
public class ResilienceConfig {

    static final String AI_CLIENT = "aiClient";

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService aiTimeoutScheduler() {
        return Executors.newScheduledThreadPool(3);
    }

    @Bean
    public TimeLimiter aiTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter(AI_CLIENT);
    }

    @Bean
    public CircuitBreaker aiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(AI_CLIENT);
    }
}
