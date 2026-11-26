package com.playlist.spotify;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.spotify")
public record SpotifyProperties(
        String clientId,
        String clientSecret,
        String baseUrl,
        String accountsUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {
}
