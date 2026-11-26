package com.playlist.recommendation;

import com.google.genai.errors.ApiException;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClientResponseException;

/**
 * Maps provider/client failures to domain exceptions by exception type and HTTP status
 * (never by free-text message matching, except Spring AI's own "HTTP nnn - ..." format).
 */
final class AiErrorMapper {

    private static final int MAX_CAUSE_DEPTH = 10;
    private static final Pattern SPRING_AI_STATUS = Pattern.compile("^HTTP (\\d{3})\\b");

    private AiErrorMapper() {
    }

    static RuntimeException map(Throwable error, String provider) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof AiProviderTimeoutException
                    || current instanceof AiProviderAuthException
                    || current instanceof AiProviderException
                    || current instanceof AiInvalidResponseException) {
                return (RuntimeException) current;
            }
            if (isTimeout(current)) {
                return new AiProviderTimeoutException("Timeout llamando a " + provider, error);
            }
            Integer status = httpStatus(current);
            if (status != null) {
                return fromStatus(status, provider, error);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return new AiProviderException("Error llamando a " + provider, error);
    }

    private static boolean isTimeout(Throwable t) {
        return t instanceof TimeoutException
                || t instanceof SocketTimeoutException
                || t instanceof HttpTimeoutException;
    }

    private static Integer httpStatus(Throwable t) {
        if (t instanceof RestClientResponseException rest) {
            return rest.getStatusCode().value();
        }
        if (t instanceof ApiException api) {
            return api.code();
        }
        if (t.getClass().getName().startsWith("org.springframework.ai.retry.") && t.getMessage() != null) {
            Matcher m = SPRING_AI_STATUS.matcher(t.getMessage());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return null;
    }

    private static RuntimeException fromStatus(int status, String provider, Throwable cause) {
        if (status == 401 || status == 403) {
            return new AiProviderAuthException("Error de autenticación con " + provider, cause);
        }
        return new AiProviderException("Error HTTP " + status + " de " + provider, cause);
    }
}
