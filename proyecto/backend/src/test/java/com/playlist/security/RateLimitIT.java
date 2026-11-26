package com.playlist.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

/** Verifies per-user limits on recommendations and /spotify/** (03 §4.5), using small limits. */
@SpringBootTest(properties = {
        "app.rate-limit.recommendations=2",
        "app.rate-limit.spotify=2",
        "app.rate-limit.lists=2",
        "app.rate-limit.me=2",
        "app.rate-limit.logout=2",
        // Unreachable port: Spotify calls fail fast and fall back, no real network is used.
        "app.spotify.base-url=http://localhost:1/v1",
        "app.spotify.accounts-url=http://localhost:1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RateLimitIT {

    @Autowired private MockMvc mockMvc;
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

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        for (String part : result.getResponse().getHeader("Set-Cookie").split(";")) {
            if (part.trim().startsWith("access_token=")) {
                return new Cookie("access_token", part.trim().substring("access_token=".length()));
            }
        }
        throw new IllegalStateException("access_token cookie not found");
    }

    @Test
    void recommendations_allowsConfiguredLimit_thenReturns429() throws Exception {
        Cookie cookie = login();

        // Each request must consume exactly one token (regression: it used to consume two).
        mockMvc.perform(get("/lists/nope/recommendations").cookie(cookie)).andExpect(status().isNotFound());
        mockMvc.perform(get("/lists/nope/recommendations").cookie(cookie)).andExpect(status().isNotFound());
        mockMvc.perform(get("/lists/nope/recommendations").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("RATE_LIMITED"));
    }

    @Test
    void lists_allowsConfiguredLimit_thenReturns429() throws Exception {
        Cookie cookie = login();

        mockMvc.perform(get("/lists").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/lists/nope").cookie(cookie)).andExpect(status().isNotFound());
        mockMvc.perform(get("/lists").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("RATE_LIMITED"));
    }

    @Test
    void me_allowsConfiguredLimit_thenReturns429() throws Exception {
        Cookie cookie = login();

        mockMvc.perform(get("/auth/me").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void logout_allowsConfiguredLimit_thenReturns429() throws Exception {
        Cookie cookie = login();

        mockMvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(post("/auth/logout").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void spotify_allowsConfiguredLimit_thenReturns429() throws Exception {
        Cookie cookie = login();

        mockMvc.perform(get("/spotify/genres").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/spotify/genres").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/spotify/genres").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("RATE_LIMITED"));
    }
}
