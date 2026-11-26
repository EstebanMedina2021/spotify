package com.playlist.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.auth.JwtProperties;
import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.auth.dto.LoginRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import jakarta.servlet.http.Cookie;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByUsername("demo")) {
            userRepository.save(User.builder()
                    .username("demo")
                    .passwordHash(passwordEncoder.encode("Demo1234!"))
                    .enabled(true)
                    .build());
        }
    }

    @Test
    void protectedEndpoint_withoutCookie_returns401() throws Exception {
        mockMvc.perform(get("/lists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("UNAUTHORIZED"));
    }

    @Test
    void tokenWithAlgNone_returns401() throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"00000000-0000-0000-0000-000000000001\"}".getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".";

        mockMvc.perform(get("/lists").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithHs512_returns401() throws Exception {
        // Build a token signed with HS512 – decoder only accepts HS256 → 401
        String headerJson = "{\"alg\":\"HS512\"}";
        String payloadJson = "{\"sub\":\"00000000-0000-0000-0000-000000000001\",\"iss\":\""
                + jwtProperties.issuer() + "\",\"exp\":" + Instant.now().plusSeconds(3600).getEpochSecond() + "}";
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        byte[] key = decodeSecret(jwtProperties.secret());
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key, "HmacSHA512"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        String token = signingInput + "." + signature;

        mockMvc.perform(get("/lists").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_returns401() throws Exception {
        // Generate a token that expired 3 minutes ago (manual JWT)
        Instant exp = Instant.now().minusSeconds(180);
        String headerJson = "{\"alg\":\"HS256\"}";
        String payloadJson = "{\"sub\":\"00000000-0000-0000-0000-000000000001\",\"iss\":\""
                + jwtProperties.issuer() + "\",\"exp\":" + exp.getEpochSecond() + "}";
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        byte[] key = decodeSecret(jwtProperties.secret());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        String token = signingInput + "." + signature;

        mockMvc.perform(get("/lists").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrf_returns403CsrfInvalid() throws Exception {
        String accessToken = loginAndGetCookie();

        // 1. Obtener CSRF token (materializa la cookie XSRF-TOKEN)
        MvcResult csrfResult = mockMvc.perform(get("/auth/csrf")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        String xsrfToken = xsrfCookie.getValue();

        // 2. POST sin header X-XSRF-TOKEN pero con la cookie XSRF-TOKEN → 403
        mockMvc.perform(post("/lists")
                        .cookie(
                            new Cookie("access_token", accessToken),
                            new Cookie("XSRF-TOKEN", xsrfToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_INVALID"));
    }

    @Test
    void anonymousRequest_issuesXsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        Cookie xsrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getValue()).isNotBlank();
    }

    @Test
    void postWithValidCsrf_doesNotClearXsrfCookie() throws Exception {
        String accessToken = loginAndGetCookie();
        String xsrfToken = "test-xsrf-token";

        MvcResult result = mockMvc.perform(post("/lists")
                        .cookie(new Cookie("access_token", accessToken),
                                new Cookie("XSRF-TOKEN", xsrfToken))
                        .header("X-XSRF-TOKEN", xsrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"CsrfKeep\",\"canciones\":[]}"))
                .andExpect(status().isCreated())
                .andReturn();

        // Regression: the default CSRF session strategy used to expire the cookie here
        Cookie after = result.getResponse().getCookie("XSRF-TOKEN");
        if (after != null) {
            assertThat(after.getMaxAge()).isNotZero();
            assertThat(after.getValue()).isNotBlank();
        }
    }

    @Test
    void healthEndpoint_isPublicButNeverExposesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());

        // Same for an authenticated user: details must stay hidden for everyone
        mockMvc.perform(get("/actuator/health").cookie(new Cookie("access_token", loginAndGetCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    private String loginAndGetCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("demo", "Demo1234!"))))
                .andExpect(status().isOk())
                .andReturn();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        for (String part : setCookie.split(";")) {
            if (part.trim().startsWith("access_token=")) {
                return part.trim().substring("access_token=".length());
            }
        }
        throw new IllegalStateException("Cookie not found");
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}