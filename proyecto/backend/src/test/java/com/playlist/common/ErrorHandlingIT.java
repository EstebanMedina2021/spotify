package com.playlist.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Client mistakes must come back as 4xx with the uniform ErrorResponse, never as 500. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ErrorHandlingIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Cookie accessToken;
    private Cookie xsrf;

    @BeforeEach
    void setUp() throws Exception {
        if (!userRepository.existsByUsername("demo")) {
            userRepository.save(User.builder()
                    .username("demo")
                    .passwordHash(passwordEncoder.encode("Demo1234!"))
                    .enabled(true)
                    .build());
        }
        xsrf = mockMvc.perform(get("/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        for (String part : login.getResponse().getHeader("Set-Cookie").split(";")) {
            if (part.trim().startsWith("access_token=")) {
                accessToken = new Cookie("access_token", part.trim().substring("access_token=".length()));
            }
        }
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.cookie(accessToken, xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    @Test
    void unknownRoute_whenAuthenticated_returns404WithUniformBody() throws Exception {
        mockMvc.perform(authed(get("/does-not-exist")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/does-not-exist"));
    }

    @Test
    void unknownRoute_whenAnonymous_returns401SoRoutesAreNotEnumerable() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("UNAUTHORIZED"));
    }

    @Test
    void unsupportedMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(authed(put("/lists")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METHOD_NOT_ALLOWED"))
                .andExpect(header().exists("Allow"));
    }

    @Test
    void unsupportedContentType_returns415() throws Exception {
        mockMvc.perform(authed(post("/lists")).contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void controlCharacterInName_returns400() throws Exception {
        mockMvc.perform(authed(post("/lists")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"a\\u0007b\",\"canciones\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.mensaje").value(containsString("nombre")));
    }

    @Test
    void controlCharacterInDescription_returns400() throws Exception {
        mockMvc.perform(authed(post("/lists")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"ok\",\"descripcion\":\"linea1\\nlinea2\",\"canciones\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("descripcion")));
    }

    @Test
    void controlCharacterInSongField_returns400() throws Exception {
        mockMvc.perform(authed(post("/lists")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"ok\",\"canciones\":[{\"titulo\":\"t\\u0000\",\"artista\":\"a\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void controlCharacterInPathVariable_returns400NotInternalError() throws Exception {
        mockMvc.perform(authed(get("/lists/a%07b")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void wrongPassword_returns401WithASpanishMessageAndNoInternalDetails() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }
    @Test
    void errorBodies_neverLeakInternals() throws Exception {
        mockMvc.perform(authed(get("/lists/a%07b")))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.not(containsString("Exception"))))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.not(containsString("index"))));
    }
}
