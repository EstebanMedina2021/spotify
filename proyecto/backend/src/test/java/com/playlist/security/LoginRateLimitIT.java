package com.playlist.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Login is throttled per username (case-insensitive) and per IP (03 §4.5). */
@SpringBootTest(properties = {
        "app.rate-limit.login-per-user=2",
        "app.rate-limit.login-per-ip=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginRateLimitIT {

    @Autowired private MockMvc mockMvc;

    private void login(String username) throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"));
    }

    @Test
    void sameUsername_isThrottledAfterLimit_evenWithDifferentCase() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"victim\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"VICTIM\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Victim\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("RATE_LIMITED"));
    }

    @Test
    void otherUsername_isNotAffected() throws Exception {
        login("blocked-user");
        login("blocked-user");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked-user\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"someone-else\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
