package com.playlist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.playlist.auth.CookieBearerTokenResolver;
import com.playlist.auth.JwtProperties;
import com.playlist.common.ErrorResponse;
import com.playlist.security.CustomAccessDeniedHandler;
import com.playlist.security.CustomAuthenticationEntryPoint;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProperties jwtProperties;
    private final CookieBearerTokenResolver cookieBearerTokenResolver;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        // Stateless: default strategy clears the XSRF cookie after each authenticated request.
                        .sessionAuthenticationStrategy((authentication, request, response) -> { })
                        .ignoringRequestMatchers("/auth/login", "/auth/logout", "/auth/me")
                )
                // The CSRF token is deferred; reading it forces the XSRF-TOKEN cookie to be written.
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                    FilterChain chain) throws ServletException, IOException {
                        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                        if (token != null) {
                            token.getToken();
                        }
                        chain.doFilter(request, response);
                    }
                }, CsrfFilter.class)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/auth/csrf").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                        .bearerTokenResolver(cookieBearerTokenResolver)
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (accessDeniedException instanceof CsrfException) {
                                response.setStatus(403);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                ErrorResponse body = new ErrorResponse(
                                        "CSRF_INVALID",
                                        "Token CSRF ausente o inválido",
                                        Instant.now(),
                                        request.getRequestURI()
                                );
                                objectMapper.writeValue(response.getOutputStream(), body);
                            } else {
                                accessDeniedHandler.handle(request, response, accessDeniedException);
                            }
                        })
                )
                .headers(headers -> {
                    headers
                            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                            .contentTypeOptions(Customizer.withDefaults())
                            .frameOptions(frame -> frame.deny())
                            .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    if (environment.acceptsProfiles(Profiles.of("prod"))) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000));
                    }
                });

        return http.build();
    }

    @Bean
    public CsrfTokenRequestHandler csrfTokenRequestHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey secretKey = secretKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKey secretKey = secretKey();
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
        return converter;
    }

    private SecretKey secretKey() {
        byte[] keyBytes = decodeSecret(jwtProperties.secret());
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}