package com.playlist.recommendation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.playlist.PlaylistService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
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

/**
 * Goes through the REAL FallbackAiClient (no @MockitoBean on the AI client), with a provider that
 * always fails with an auth error. Proves the wiring controller -> service -> decorator -> provider.
 */
@SpringBootTest(properties = "app.ai.mock.scenario=AUTH_ERROR")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendationFallbackIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlaylistService playlistService;
    @Autowired private PromptTemplates promptTemplates;

    @Test
    void providerAuthFailure_returns200WithGenericSuggestionsAndTheRightReason() throws Exception {
        // Arrange
        User user = userRepository.save(User.builder()
                .username("fallback-user")
                .passwordHash(passwordEncoder.encode("Demo1234!"))
                .enabled(true)
                .build());
        UUID ownerId = user.getId();
        playlistService.crear(new com.playlist.playlist.dto.PlaylistRequest("Rock", null, List.of(
                new com.playlist.playlist.dto.CancionRequest("Bohemian Rhapsody", "Queen", null, null, "rock"))), ownerId);
        Cookie cookie = login("fallback-user");

        // Act + Assert
        mockMvc.perform(get("/lists/Rock/recommendations").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("PROVIDER_AUTH_ERROR"))
                .andExpect(jsonPath("$.fuente").value("FALLBACK"))
                .andExpect(jsonPath("$.recomendaciones").isNotEmpty())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void theActivePromptVersion_isV2() {
        org.assertj.core.api.Assertions.assertThat(promptTemplates.getPromptVersion()).isEqualTo("v2");
    }
    private Cookie login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        for (String part : result.getResponse().getHeader("Set-Cookie").split(";")) {
            if (part.trim().startsWith("access_token=")) {
                return new Cookie("access_token", part.trim().substring("access_token=".length()));
            }
        }
        throw new IllegalStateException("access_token cookie not found");
    }
}
