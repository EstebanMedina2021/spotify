package com.playlist.spotify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Spotify accounts + API are both simulated by one WireMock server. */
@SpringBootTest(properties = {
        "app.spotify.client-id=test-client-id",
        "app.spotify.client-secret=test-client-secret",
        "app.spotify.read-timeout=1s"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpotifyControllerIT {

    private static WireMockServer wireMock;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SpotifyService spotifyService;
    @Autowired private SpotifyTokenService tokenService;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void spotifyUrls(DynamicPropertyRegistry registry) {
        registry.add("app.spotify.accounts-url", () -> "http://localhost:" + wireMock.port());
        registry.add("app.spotify.base-url", () -> "http://localhost:" + wireMock.port() + "/v1");
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        spotifyService.clearCache();
        tokenService.invalidate();
        if (!userRepository.existsByUsername("demo")) {
            userRepository.save(User.builder()
                    .username("demo")
                    .passwordHash(passwordEncoder.encode("Demo1234!"))
                    .enabled(true)
                    .build());
        }
        stubToken();
    }

    private void stubToken() {
        wireMock.stubFor(post(urlPathEqualTo("/api/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
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

    private static final String SEARCH_BODY = """
            {"tracks":{"items":[
              {"id":"2JiDi0qAXsPwhPqA2qaKGt","name":"Bohemian Rhapsody",
               "artists":[{"name":"Queen"}],"album":{"name":"A Night At The Opera"}},
              {"id":"3xtPQCHJQ3o70PeVAaZSG2","name":"Queencard",
               "artists":[{"name":"i-dle"},{"name":"Other"}],"album":{"name":"I feel"}},
              {"name":"No id, discarded","artists":[],"album":{"name":"x"}}
            ]}}""";

    // ---- genres ----

    @Test
    void genres_whenSpotifyAvailable_returnsSpotifyList() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/recommendations/available-genre-seeds"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"genres\":[\"rock\",\"pop\"]}")));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/genres").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generos[0]").value("rock"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.motivoFallback").doesNotExist());
    }

    @Test
    void genres_whenEndpointNotAvailable_returnsCuratedList() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/recommendations/available-genre-seeds"))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/genres").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("ENDPOINT_UNAVAILABLE"))
                .andExpect(jsonPath("$.generos").isNotEmpty());
    }

    @Test
    void genres_when403_returnsCuratedList() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/recommendations/available-genre-seeds"))
                .willReturn(aResponse().withStatus(403)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/genres").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoFallback").value("ENDPOINT_UNAVAILABLE"));
    }

    @Test
    void genres_whenSpotifyFails500_returnsCuratedListProviderDown() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/recommendations/available-genre-seeds"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/genres").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.motivoFallback").value("PROVIDER_DOWN"));
    }

    // ---- search ----

    @Test
    void search_ok_mapsTracks() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SEARCH_BODY)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").param("limit", "5")
                        .cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].titulo").value("Bohemian Rhapsody"))
                .andExpect(jsonPath("$.resultados[0].artista").value("Queen"))
                .andExpect(jsonPath("$.resultados[0].album").value("A Night At The Opera"))
                .andExpect(jsonPath("$.resultados[0].spotifyId").value("2JiDi0qAXsPwhPqA2qaKGt"))
                .andExpect(jsonPath("$.resultados[1].artista").value("i-dle, Other"));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/v1/search"))
                .withQueryParam("q", equalTo("queen"))
                .withQueryParam("type", equalTo("track"))
                .withQueryParam("limit", equalTo("5"))
                .withHeader("Authorization", equalTo("Bearer tok-1")));
    }

    @Test
    void search_specialCharactersInQuery_areEncodedAsSingleParameter() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"tracks\":{\"items\":[]}}")));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "a&limit=50&type=artist")
                        .cookie(login()))
                .andExpect(status().isOk());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/v1/search"))
                .withQueryParam("q", equalTo("a&limit=50&type=artist"))
                .withQueryParam("type", equalTo("track"))
                .withQueryParam("limit", equalTo("10")));
    }

    @Test
    void search_emptyQuery_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "").cookie(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "   ").cookie(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void search_missingQuery_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").cookie(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void search_queryLongerThan100_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "a".repeat(101))
                        .cookie(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void search_queryWithControlCharacters_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "abc\u0007def")
                        .cookie(login()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_limitAbove10_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").param("limit", "11")
                        .cookie(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }

    @Test
    void search_limitBelow1_orNotNumeric_returns400() throws Exception {
        Cookie cookie = login();
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").param("limit", "0")
                        .cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").param("limit", "abc")
                        .cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_whenSpotifyRateLimits_returns429WithRetryAfter() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse()
                .withStatus(429).withHeader("Retry-After", "7")));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").cookie(login()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.codigo").value("SPOTIFY_RATE_LIMITED"));
    }

    @Test
    void search_whenSpotifyFails500_returns503() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").cookie(login()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SPOTIFY_UNAVAILABLE"));
    }

    @Test
    void search_whenSpotifyTimesOut_returns503() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse()
                .withFixedDelay(3000).withHeader("Content-Type", "application/json")
                .withBody(SEARCH_BODY)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").cookie(login()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SPOTIFY_UNAVAILABLE"));
    }

    @Test
    void search_whenTokenEndpointFails_returns503() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/api/token")).willReturn(aResponse().withStatus(400)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").cookie(login()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SPOTIFY_UNAVAILABLE"));
    }

    // ---- token handling ----

    @Test
    void token_isCachedAcrossRequests_andSentAsBasicAuth() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SEARCH_BODY)));
        Cookie cookie = login();

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "a").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "b").cookie(cookie))
                .andExpect(status().isOk());

        // base64("test-client-id:test-client-secret")
        String expected = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("test-client-id:test-client-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/token"))
                .withHeader("Authorization", equalTo(expected))
                .withRequestBody(equalTo("grant_type=client_credentials")));
    }

    @Test
    void token_isRenewedOnceWhenSpotifyReturns401() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).inScenario("expired")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("renewed"));
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).inScenario("expired")
                .whenScenarioStateIs("renewed")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(SEARCH_BODY)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(2));

        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/api/token")));
    }

    @Test
    void persistent401_doesNotLoopForever_returns503() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search")).willReturn(aResponse().withStatus(401)));

        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen").cookie(login()))
                .andExpect(status().isServiceUnavailable());

        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/v1/search")))).hasSize(2);
    }

    // ---- security ----

    @Test
    void endpoints_withoutCookie_return401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/genres"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/spotify/search").param("q", "queen"))
                .andExpect(status().isUnauthorized());
    }
}
