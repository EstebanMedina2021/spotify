package com.playlist.recommendation;

import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.concurrent.TimeoutException;

/**
 * Single place that turns a failure into a {@code motivoFallback}.
 *
 * <p>It walks the cause chain from the outside and stops at the first domain exception. Walking to
 * the root cause would be wrong: our exceptions wrap the network error (e.g. a socket timeout),
 * which would hide the domain meaning.
 */
final class AiFailureClassifier {

    static final String TIMEOUT = "TIMEOUT";
    static final String PROVIDER_AUTH_ERROR = "PROVIDER_AUTH_ERROR";
    static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";
    static final String PROVIDER_DOWN = "PROVIDER_DOWN";

    private static final int MAX_CAUSE_DEPTH = 10;

    private AiFailureClassifier() {
    }

    static String motivoFor(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof AiProviderTimeoutException || current instanceof TimeoutException) {
                return TIMEOUT;
            }
            if (current instanceof AiProviderAuthException) {
                return PROVIDER_AUTH_ERROR;
            }
            if (current instanceof AiInvalidResponseException) {
                return INVALID_RESPONSE;
            }
            if (current instanceof CallNotPermittedException) {
                return CIRCUIT_OPEN;
            }
            if (current instanceof AiProviderException) {
                return PROVIDER_DOWN;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return PROVIDER_DOWN;
    }
}
