package com.playlist.playlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.auth.dto.LoginRequest;
import com.playlist.playlist.dto.CancionRequest;
import com.playlist.playlist.dto.PlaylistRequest;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
class PlaylistControllerIT {

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
    void crearPlaylist_ok_returns201WithLocation() throws Exception {
        Session s = login();

        PlaylistRequest req = new PlaylistRequest(
                "Lista 1",
                "Mi lista",
                List.of(new CancionRequest("Bohemian Rhapsody", "Queen",
                        "A Night at the Opera", "1975", "Rock"))
        );

        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value("Lista 1"))
                .andExpect(jsonPath("$.canciones[0].titulo").value("Bohemian Rhapsody"));
    }

    @Test
    void crearPlaylist_nombreBlank_returns400() throws Exception {
        Session s = login();
        PlaylistRequest req = new PlaylistRequest("  ", "desc", null);

        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void crearPlaylist_duplicado_returns409() throws Exception {
        Session s = login();
        PlaylistRequest req = new PlaylistRequest("Lista Dup", null, null);

        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLAYLIST_DUPLICATED"));
    }

    @Test
    void listarPlaylists_soloDelUsuario() throws Exception {
        Session s = login();
        mockMvc.perform(get("/lists").cookie(s.accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerPlaylist_inexistente_returns404() throws Exception {
        Session s = login();
        mockMvc.perform(get("/lists/NoExiste").cookie(s.accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void eliminarPlaylist_ok_returns204() throws Exception {
        Session s = login();
        PlaylistRequest req = new PlaylistRequest("Para Borrar", null, null);
        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/lists/Para Borrar")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarPlaylist_inexistente_returns404() throws Exception {
        Session s = login();
        mockMvc.perform(delete("/lists/NoExiste")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue()))
                .andExpect(status().isNotFound());
    }

    private record Session(Cookie accessToken, Cookie xsrfToken) {}

    private Session login() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("demo", "Demo1234!"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = loginResult.getResponse().getCookie("access_token");

        MvcResult csrfResult = mockMvc.perform(get("/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        return new Session(access, xsrf);
    }
}