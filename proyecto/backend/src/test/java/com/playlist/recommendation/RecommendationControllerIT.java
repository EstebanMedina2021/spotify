package com.playlist.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.auth.dto.LoginRequest;
import com.playlist.playlist.Playlist;
import com.playlist.playlist.PlaylistRepository;
import com.playlist.playlist.dto.CancionRequest;
import com.playlist.playlist.dto.PlaylistRequest;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import com.playlist.recommendation.exception.AiProviderAuthException;
import com.playlist.recommendation.exception.AiProviderException;
import com.playlist.recommendation.exception.AiProviderTimeoutException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendationControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AiRecommendationClient aiRecommendationClient;

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
    void recomendar_happyPath_returns200WithFallbackFalse() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista Rock", "Rock");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        RecommendationResult.ok(
                                List.of(new RecomendacionItem("Test", "Artist", "Rock", "razón")),
                                "MOCK",
                                "v1")));

        mockMvc.perform(get("/lists/Lista Rock/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistNombre").value("Lista Rock"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.motivoFallback").doesNotExist())
                .andExpect(jsonPath("$.recomendaciones[0].titulo").value("Test"));
    }

    @Test
    void recomendar_listaVacia_returns200WithMensaje() throws Exception {
        Session s = login();
        crearPlaylistVacia(s, "Lista Vacía");

        mockMvc.perform(get("/lists/Lista Vacía/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.motivoFallback").doesNotExist())
                .andExpect(jsonPath("$.mensaje").value(
                        "La lista no contiene canciones para generar recomendaciones."))
                .andExpect(jsonPath("$.recomendaciones").isEmpty());

        verify(aiRecommendationClient, never()).recommend(any(), anyString());
    }

    @Test
    void recomendar_playlistInexistente_returns404() throws Exception {
        Session s = login();
        mockMvc.perform(get("/lists/NoExiste/recommendations").cookie(s.accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void recomendar_timeout_returns200FallbackTrue() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista Timeout", "Pop");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new AiProviderTimeoutException("timeout")));

        mockMvc.perform(get("/lists/Lista Timeout/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("TIMEOUT"))
                .andExpect(jsonPath("$.fuente").value("FALLBACK"));
    }

    @Test
    void recomendar_providerError_returns200FallbackTrue() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista Error", "Pop");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new AiProviderException("error")));

        mockMvc.perform(get("/lists/Lista Error/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("PROVIDER_DOWN"));
    }

    @Test
    void recomendar_authError_returns200FallbackTrue() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista Auth", "Pop");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new AiProviderAuthException("auth")));

        mockMvc.perform(get("/lists/Lista Auth/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("PROVIDER_AUTH_ERROR"));
    }

    @Test
    void recomendar_invalidResponse_returns200FallbackTrue() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista Invalid", "Pop");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new AiInvalidResponseException("invalid")));

        mockMvc.perform(get("/lists/Lista Invalid/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("INVALID_RESPONSE"));
    }

    @Test
    void recomendar_cache_hitAvoidsSecondCall() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista Cache", "Rock");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        RecommendationResult.ok(
                                List.of(new RecomendacionItem("Test", "Artist", "Rock", "r")),
                                "MOCK",
                                "v1")));

        // Primera llamada
        mockMvc.perform(get("/lists/Lista Cache/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk());

        // Segunda llamada → debe usar caché, no debe llamar al cliente
        mockMvc.perform(get("/lists/Lista Cache/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk());

        verify(aiRecommendationClient, times(1)).recommend(any(), anyString());
    }

    @Test
    void recomendar_fallback_noSeCachea() throws Exception {
        Session s = login();
        crearPlaylistConCanciones(s, "Lista NoCache", "Rock");

        when(aiRecommendationClient.recommend(any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new AiProviderTimeoutException("timeout")));

        // Primera llamada → fallback, no se cachea
        mockMvc.perform(get("/lists/Lista NoCache/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true));

        // Segunda llamada → debe volver a llamar al cliente
        mockMvc.perform(get("/lists/Lista NoCache/recommendations").cookie(s.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true));

        verify(aiRecommendationClient, times(2)).recommend(any(), anyString());
    }

    // ---- helpers ----

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

    private void crearPlaylistConCanciones(Session s, String nombre, String genero) throws Exception {
        PlaylistRequest req = new PlaylistRequest(
                nombre,
                "desc",
                List.of(new CancionRequest("Test", "Artist", "Album", "2020", genero)));
        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private void crearPlaylistVacia(Session s, String nombre) throws Exception {
        PlaylistRequest req = new PlaylistRequest(nombre, "desc", null);
        mockMvc.perform(post("/lists")
                        .cookie(s.accessToken, s.xsrfToken)
                        .header("X-XSRF-TOKEN", s.xsrfToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}