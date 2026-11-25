package com.playlist.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CancionRepositoryTest {

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private CancionRepository cancionRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .username("owner")
                .passwordHash("$2a$12$dummy")
                .enabled(true)
                .build());
    }

    @Test
    void playlistWithThreeSongs_preservesOrderByPosicion() {
        Playlist playlist = Playlist.builder()
                .nombre("Ordered")
                .nombreNormalizado("ordered")
                .owner(owner)
                .build();

        Cancion c1 = Cancion.builder()
                .titulo("First")
                .artista("Artist A")
                .posicion(0)
                .build();
        Cancion c2 = Cancion.builder()
                .titulo("Second")
                .artista("Artist B")
                .posicion(1)
                .build();
        Cancion c3 = Cancion.builder()
                .titulo("Third")
                .artista("Artist C")
                .posicion(2)
                .build();

        playlist.addCancion(c1);
        playlist.addCancion(c2);
        playlist.addCancion(c3);

        Playlist saved = playlistRepository.saveAndFlush(playlist);

        Playlist reloaded = playlistRepository.findById(saved.getId()).orElseThrow();
        List<Cancion> canciones = reloaded.getCanciones();

        assertThat(canciones).hasSize(3);
        assertThat(canciones.get(0).getTitulo()).isEqualTo("First");
        assertThat(canciones.get(1).getTitulo()).isEqualTo("Second");
        assertThat(canciones.get(2).getTitulo()).isEqualTo("Third");
        assertThat(canciones.get(0).getPosicion()).isEqualTo(0);
        assertThat(canciones.get(1).getPosicion()).isEqualTo(1);
        assertThat(canciones.get(2).getPosicion()).isEqualTo(2);
    }
}