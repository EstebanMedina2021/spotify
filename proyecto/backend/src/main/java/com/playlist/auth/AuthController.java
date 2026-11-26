package com.playlist.auth;

import com.playlist.auth.dto.LoginRequest;
import com.playlist.auth.dto.LoginResponse;
import com.playlist.auth.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CookieFactory cookieFactory;
    private final Environment environment;
    private final CookieBearerTokenResolver tokenResolver;

    
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok(Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName()
        ));
    }
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = authService.authenticate(request.username(), request.password());
        String token = jwtService.generateToken(user);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.expirationMinutes() * 60);
        long maxAge = jwtProperties.expirationMinutes() * 60;
        boolean secure = isSecure();

        ResponseCookie cookie = cookieFactory.createAccessTokenCookie(token, maxAge, secure);

        LoginResponse body = new LoginResponse(user.getUsername(), expiresAt.toString());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        boolean secure = isSecure();
        ResponseCookie cookie = cookieFactory.clearAccessTokenCookie(secure);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("No autenticado");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        String token = tokenResolver.resolve(request);
        MeResponse response = authService.me(userId, token);
        return ResponseEntity.ok(response);
    }

    private boolean isSecure() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}