package com.playlist.common;

import com.playlist.common.exception.DuplicatePlaylistException;
import com.playlist.common.exception.InvalidTextException;
import com.playlist.common.exception.PlaylistNotFoundException;
import com.playlist.common.exception.RateLimitExceededException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import com.playlist.spotify.exception.SpotifyRateLimitedException;
import com.playlist.spotify.exception.SpotifyUnavailableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.playlist.common.exception.RateLimitExceededException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(PlaylistNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            PlaylistNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCodes.PLAYLIST_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicatePlaylistException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicatePlaylistException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCodes.PLAYLIST_DUPLICATED, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "JSON inválido", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI());
        return build(HttpStatus.CONFLICT, ErrorCodes.PLAYLIST_DUPLICATED,
                "Conflicto de datos", request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        String mensaje = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName()
                                + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, mensaje, request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(
            Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "Parámetro inválido o ausente", request);
    }

    @ExceptionHandler(SpotifyRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleSpotifyRateLimited(
            SpotifyRateLimitedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResponse(
                        ErrorCodes.SPOTIFY_RATE_LIMITED,
                        "Spotify está limitando las solicitudes. Intenta de nuevo más tarde.",
                        Instant.now(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(SpotifyUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSpotifyUnavailable(
            SpotifyUnavailableException ex, HttpServletRequest request) {
        log.warn("Spotify unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.SPOTIFY_UNAVAILABLE,
                "Spotify no está disponible en este momento", request);
    }

    @ExceptionHandler(InvalidTextException.class)
    public ResponseEntity<ErrorResponse> handleInvalidText(
            InvalidTextException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "El texto contiene caracteres no permitidos", request);
    }

    /**
     * Spring MVC protocol errors (unknown route, wrong method, unsupported media type...) carry
     * their own HTTP status through {@link org.springframework.web.ErrorResponse}. They are client
     * errors and must not be reported as 500.
     */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ErrorResponse> handleServletException(
            ServletException ex, HttpServletRequest request) {
        if (!(ex instanceof org.springframework.web.ErrorResponse protocolError)) {
            return handleGeneric(ex, request);
        }
        HttpStatus status = HttpStatus.valueOf(protocolError.getStatusCode().value());
        log.warn("{} {} -> {}", request.getMethod(), request.getRequestURI(), status.value());
        ErrorResponse body = new ErrorResponse(
                codeFor(status), messageFor(status), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(status).headers(protocolError.getHeaders()).body(body);
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ErrorCodes.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCodes.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> ErrorCodes.NOT_ACCEPTABLE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCodes.INTERNAL_ERROR : ErrorCodes.VALIDATION_ERROR;
        };
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso no encontrado";
            case METHOD_NOT_ALLOWED -> "Método HTTP no permitido para este recurso";
            case NOT_ACCEPTABLE -> "Formato de respuesta no soportado";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
            default -> status.is5xxServerError() ? "Error interno" : "Solicitud inválida";
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "Error interno", request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(new ErrorResponse(
                        ErrorCodes.RATE_LIMITED,
                        "Demasiadas solicitudes",
                        Instant.now(),
                        request.getRequestURI()
                ));
    }

    
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String codigo,
                                                 String mensaje, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                codigo,
                mensaje,
                Instant.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}