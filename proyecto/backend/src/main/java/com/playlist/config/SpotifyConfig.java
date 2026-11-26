package com.playlist.config;

import com.playlist.spotify.SpotifyProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClients for Spotify with explicit timeouts (04 §9). Wire logging is not enabled,
 * so tokens and credentials never reach the logs.
 */
@Configuration
public class SpotifyConfig {

    public static final String API_CLIENT = "spotifyApiRestClient";
    public static final String ACCOUNTS_CLIENT = "spotifyAccountsRestClient";

    @Bean(API_CLIENT)
    public RestClient spotifyApiRestClient(SpotifyProperties props) {
        return build(props, props.baseUrl());
    }

    @Bean(ACCOUNTS_CLIENT)
    public RestClient spotifyAccountsRestClient(SpotifyProperties props) {
        return build(props, props.accountsUrl());
    }

    private RestClient build(SpotifyProperties props, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
