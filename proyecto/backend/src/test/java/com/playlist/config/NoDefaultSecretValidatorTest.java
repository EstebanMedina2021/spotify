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

    @Test
    void exampleSecretFromEnvExample_failsToStart() {
        // The old .env.example value is long enough (>= 256 bits) but public, so it must be rejected.
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=change-me-to-a-secret-at-least-32-chars-long-256-bits",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=mock"
                )
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("example placeholder"));
    }

    @Test
    void angleBracketPlaceholder_failsToStart() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=<generate-a-random-base64-secret>",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=mock"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void strongRandomSecret_startsNormally() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=q8Zr1m0vKf3Yb7XcLw2PtN5uHa9GdEjS6iOoRkTyUe4VnMxBzCsAh1DlWgF8JpQ3",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=mock"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void geminiWithoutModel_failsToStart() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=q8Zr1m0vKf3Yb7XcLw2PtN5uHa9GdEjS6iOoRkTyUe4VnMxBzCsAh1DlWgF8JpQ3",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=gemini",
                        "GEMINI_AI_API_KEY=present-for-the-test"
                )
                .run(context -> assertThat(context).getFailure().hasStackTraceContaining("GEMINI_AI_MODEL"));
    }

    @Test
    void geminiWithKeyAndModel_startsNormally() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=q8Zr1m0vKf3Yb7XcLw2PtN5uHa9GdEjS6iOoRkTyUe4VnMxBzCsAh1DlWgF8JpQ3",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=gemini",
                        "GEMINI_AI_API_KEY=present-for-the-test",
                        "GEMINI_AI_MODEL=some-current-model"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void ollamaNeedsNeitherKeyNorModelVariable() {
        contextRunner
                .withPropertyValues(
                        "app.jwt.secret=q8Zr1m0vKf3Yb7XcLw2PtN5uHa9GdEjS6iOoRkTyUe4VnMxBzCsAh1DlWgF8JpQ3",
                        "app.jwt.expiration-minutes=15",
                        "app.jwt.issuer=test",
                        "app.ai.provider=ollama",
                        "OLLAMA_BASE_URL=http://localhost:11434"
                )
                .run(context -> assertThat(context).hasNotFailed());
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