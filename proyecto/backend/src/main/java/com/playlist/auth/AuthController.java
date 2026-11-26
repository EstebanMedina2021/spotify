package com.playlist.auth;

import com.playlist.auth.dto.LoginRequest;
import com.playlist.auth.dto.LoginResponse;
import com.playlist.auth.dto.MeResponse;
import jakarta.validation.Valid;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CookieFactory cookieFactory;
    private final Environment environment;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = authService.authenticate(request.username(), request.password());
        IssuedToken token = jwtService.generateToken(user);
        long maxAge = jwtProperties.expirationMinutes() * 60;

        ResponseCookie cookie = cookieFactory.createAccessTokenCookie(token.value(), maxAge, isSecure());
        LoginResponse body = new LoginResponse(user.getUsername(), token.expiresAt().toString());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = cookieFactory.clearAccessTokenCookie(isSecure());
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /** Also the way for a client to obtain the first XSRF-TOKEN cookie (written on every request). */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("No autenticado");
        }
        // The token was already validated and decoded by the resource server: reuse its claims.
        MeResponse response = authService.me(UUID.fromString(jwt.getSubject()), jwt.getExpiresAt());
        return ResponseEntity.ok(response);
    }

    private boolean isSecure() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
