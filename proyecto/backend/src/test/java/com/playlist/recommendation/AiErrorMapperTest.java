package com.playlist.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ApiException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class AiErrorMapperTest {

    @Test
    void socketTimeoutNestedInResourceAccess_isTimeout() {
        var ex = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

        assertThat(AiErrorMapper.map(new RuntimeException(ex), "X")).isInstanceOf(AiProviderTimeoutException.class);
    }

    @Test
    void timeoutException_isTimeout() {
        assertThat(AiErrorMapper.map(new TimeoutException(), "X")).isInstanceOf(AiProviderTimeoutException.class);
    }

    @Test
    void http401And403_areAuthErrors() {
        assertThat(AiErrorMapper.map(new HttpClientErrorException(HttpStatus.UNAUTHORIZED), "X"))
                .isInstanceOf(AiProviderAuthException.class);
        assertThat(AiErrorMapper.map(new RuntimeException(new HttpClientErrorException(HttpStatus.FORBIDDEN)), "X"))
                .isInstanceOf(AiProviderAuthException.class);
    }

    @Test
    void genAiApiException_usesStatusCode() {
        assertThat(AiErrorMapper.map(new ApiException(403, "PERMISSION_DENIED", "denied"), "X"))
                .isInstanceOf(AiProviderAuthException.class);
        assertThat(AiErrorMapper.map(new ApiException(503, "UNAVAILABLE", "down"), "X"))
                .isExactlyInstanceOf(AiProviderException.class);
    }

    @Test
    void messageWithPermissionWord_isNotMisclassified() {
        assertThat(AiErrorMapper.map(new RuntimeException("permission 401 unauthorized"), "X"))
                .isExactlyInstanceOf(AiProviderException.class);
    }

    @Test
    void http500_isProviderError() {
        assertThat(AiErrorMapper.map(new HttpClientErrorException(HttpStatus.BAD_GATEWAY), "X"))
                .isExactlyInstanceOf(AiProviderException.class);
    }
}
