package com.playlist.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.playlist.recommendation.RecommendationResult;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public com.github.benmanes.caffeine.cache.Cache<String, RecommendationResult> recommendationCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}