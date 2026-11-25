package com.playlist.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class PlaylistRepositoryTest {

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(User.builder()
                .username("user1")
                .passwordHash("$2a$12$dummyhashforuser1")
                .enabled(true)
                .build());

        user2 = userRepository.save(User.builder()
                .username("user2")
                .passwordHash("$2a$12$dummyhashforuser2")
                .enabled(true)
                .build());
    }

    @Test
    void sameNormalizedNameForDifferentOwners_isAllowed() {
        Playlist p1 = Playlist.builder()
                .nombre("Rock Classics")
                .nombreNormalizado("rock classics")
                .owner(user1)
                .build();
        Playlist p2 = Playlist.builder()
                .nombre("Rock Classics")
                .nombreNormalizado("rock classics")
                .owner(user2)
                .build();

        playlistRepository.save(p1);
        playlistRepository.save(p2);

        assertThat(playlistRepository.findAll()).hasSize(2);
    }

    @Test
    void sameNormalizedNameForSameOwner_throwsDataIntegrityViolation() {
        Playlist p1 = Playlist.builder()
                .nombre("Rock Classics")
                .nombreNormalizado("rock classics")
                .owner(user1)
                .build();
        playlistRepository.saveAndFlush(p1);

        Playlist p2 = Playlist.builder()
                .nombre("Rock Classics")
                .nombreNormalizado("rock classics")
                .owner(user1)
                .build();

        assertThatThrownBy(() -> playlistRepository.saveAndFlush(p2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByOwnerIdOrderByCreatedAtDesc_returnsOnlyOwnerPlaylists() {
        Playlist p1 = playlistRepository.save(Playlist.builder()
                .nombre("Playlist A")
                .nombreNormalizado("playlist a")
                .owner(user1)
                .build());
        Playlist p2 = playlistRepository.save(Playlist.builder()
                .nombre("Playlist B")
                .nombreNormalizado("playlist b")
                .owner(user1)
                .build());
        playlistRepository.save(Playlist.builder()
                .nombre("Other")
                .nombreNormalizado("other")
                .owner(user2)
                .build());

        List<Playlist> result = playlistRepository.findAllByOwnerIdOrderByCreatedAtDesc(user1.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Playlist::getId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId());
    }

    @Test
    void findByOwnerIdAndNombreNormalizado_foundAndEmpty() {
        playlistRepository.save(Playlist.builder()
                .nombre("My List")
                .nombreNormalizado("my list")
                .owner(user1)
                .build());

        Optional<Playlist> found = playlistRepository
                .findByOwnerIdAndNombreNormalizado(user1.getId(), "my list");
        Optional<Playlist> empty = playlistRepository
                .findByOwnerIdAndNombreNormalizado(user1.getId(), "nonexistent");

        assertThat(found).isPresent();
        assertThat(found.get().getNombre()).isEqualTo("My List");
        assertThat(empty).isEmpty();
    }

    @Test
    void findByIdAndOwnerId_foundAndEmpty() {
        Playlist saved = playlistRepository.save(Playlist.builder()
                .nombre("Owned")
                .nombreNormalizado("owned")
                .owner(user1)
                .build());

        Optional<Playlist> found = playlistRepository.findByIdAndOwnerId(saved.getId(), user1.getId());
        Optional<Playlist> wrongOwner = playlistRepository.findByIdAndOwnerId(saved.getId(), user2.getId());
        Optional<Playlist> unknownId = playlistRepository.findByIdAndOwnerId(UUID.randomUUID(), user1.getId());

        assertThat(found).isPresent();
        assertThat(wrongOwner).isEmpty();
        assertThat(unknownId).isEmpty();
    }
}