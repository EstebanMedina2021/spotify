package com.playlist.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionExpiryIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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
    void loginAndMe_reportTheSameExpiry_equalToTheJwtExpClaim() throws Exception {
        // Arrange + Act
        Instant before = Instant.now();
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String loginExpiresAt = field(login, "expiresAt");
        Cookie accessToken = login.getResponse().getCookie("access_token");

        MvcResult me = mockMvc.perform(get("/auth/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String meExpiresAt = field(me, "expiresAt");

        // Assert: same value from both endpoints, and it is exactly the token's exp claim
        assertThat(meExpiresAt).isEqualTo(loginExpiresAt);
        assertThat(Instant.parse(loginExpiresAt)).isEqualTo(Instant.ofEpochSecond(expClaim(accessToken.getValue())));
        assertThat(Instant.parse(loginExpiresAt))
                .isBetween(before.plusSeconds(15 * 60 - 2), Instant.now().plusSeconds(15 * 60));
    }

    private String field(MvcResult result, String name) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(name).asText();
    }

    private long expClaim(String jwt) throws Exception {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        return objectMapper.readTree(payload).get("exp").asLong();
    }
}
