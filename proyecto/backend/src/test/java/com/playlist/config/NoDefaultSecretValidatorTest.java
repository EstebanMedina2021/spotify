package com.playlist.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.playlist.auth.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class NoDefaultSecretValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void missingJwtSecret_failsToStart() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=mock"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shortJwtSecret_failsToStart() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=tooshort",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=mock"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {

        @Bean
        NoDefaultSecretValidator noDefaultSecretValidator(JwtProperties jwtProperties,
                                                           Environment environment) {
            return new NoDefaultSecretValidator(jwtProperties, environment);
        }
    }
}