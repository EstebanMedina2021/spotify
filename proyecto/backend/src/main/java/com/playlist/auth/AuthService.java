package com.playlist.auth;

import com.playlist.auth.dto.MeResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Fixed BCrypt hash used only to equalize timing when the username does not exist.
     * Corresponds to a dummy password; never used for real authentication.
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2y$12$8521c9yL8edejntbNDRBWuDiphtdoeKuG0t0FhEnwlAf0tgiarR3i";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthenticatedUser authenticate(String username, String password) {
        loginAttemptLimiter.check(username);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            return (AuthenticatedUser) authentication.getPrincipal();
        } catch (BadCredentialsException ex) {
            // Equalize timing for non-existent users
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    public MeResponse me(UUID userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        Instant expiresAt = jwtService.extractExpiration(token);
        return new MeResponse(user.getUsername(), expiresAt.toString());
    }
}