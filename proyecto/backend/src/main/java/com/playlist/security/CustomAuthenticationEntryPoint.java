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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Do NOT add WWW-Authenticate with error_description

        ErrorResponse body = new ErrorResponse(
                ErrorCodes.UNAUTHORIZED,
                "Autenticación requerida",
                Instant.now(),
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}