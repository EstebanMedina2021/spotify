package com.playlist.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.common.ErrorCodes;
import com.playlist.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        boolean isCsrf = accessDeniedException instanceof CsrfException
                || (accessDeniedException.getCause() instanceof CsrfException);

        String codigo = isCsrf ? ErrorCodes.CSRF_INVALID : ErrorCodes.FORBIDDEN;
        String mensaje = isCsrf
                ? "Token CSRF ausente o inválido"
                : "Acceso denegado";

        ErrorResponse body = new ErrorResponse(
                codigo,
                mensaje,
                Instant.now(),
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}