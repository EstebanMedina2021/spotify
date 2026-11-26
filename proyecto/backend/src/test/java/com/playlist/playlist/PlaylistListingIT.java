package com.playlist.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.playlist.dto.CancionRequest;
import com.playlist.playlist.dto.PlaylistRequest;
import com.playlist.playlist.dto.PlaylistSummary;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Not transactional: data is committed, so the listing runs against a clean persistence context
 * and any lazy loading would show up as extra SQL statements.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class PlaylistListingIT {

    @Autowired private PlaylistService playlistService;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User newUser() {
        return userRepository.save(User.builder()
                .username("listing-" + UUID.randomUUID())
                .passwordHash("not-used")
                .enabled(true)
                .build());
    }

    private void create(User owner, String name, int songs) throws InterruptedException {
        List<CancionRequest> canciones = new ArrayList<>();
        for (int i = 0; i < songs; i++) {
            canciones.add(new CancionRequest("Song " + i, "Artist", null, null, "rock"));
        }
        playlistService.crear(new PlaylistRequest(name, null, canciones), owner.getId());
        Thread.sleep(5); // distinct createdAt so the ordering assertion is meaningful
    }

    private void cleanUp(User... owners) {
        for (User owner : owners) {
            playlistRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId()).forEach(playlistRepository::delete);
            userRepository.delete(owner);
        }
    }

    @Test
    void listing_usesASingleStatementNoMatterHowManyPlaylists() throws Exception {
        User owner = newUser();
        try {
            // Arrange: 5 playlists with songs
            for (int i = 1; i <= 5; i++) {
                create(owner, "Lista " + i, i);
            }
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();

            // Act
            List<PlaylistSummary> summaries = playlistService.listar(owner.getId());

            // Assert: it used to be 1 + one query per playlist to count the songs
            assertThat(summaries).hasSize(5);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            cleanUp(owner);
        }
    }

    @Test
    void listing_returnsCountsNewestFirstAndOnlyTheOwnersPlaylists() throws Exception {
        User owner = newUser();
        User other = newUser();
        try {
            create(owner, "Primera", 2);
            create(owner, "Vacia", 0);
            create(owner, "Ultima", 3);
            create(other, "Ajena", 4);

            List<PlaylistSummary> summaries = playlistService.listar(owner.getId());

            assertThat(summaries).extracting(PlaylistSummary::nombre).containsExactly("Ultima", "Vacia", "Primera");
            assertThat(summaries).extracting(PlaylistSummary::numeroCanciones).containsExactly(3, 0, 2);
            assertThat(summaries).extracting(PlaylistSummary::nombre).doesNotContain("Ajena");
        } finally {
            cleanUp(owner, other);
        }
    }

    @Test
    void listing_ofAUserWithoutPlaylists_isEmpty() {
        User owner = newUser();
        try {
            assertThat(playlistService.listar(owner.getId())).isEmpty();
        } finally {
            cleanUp(owner);
        }
    }
}
