package com.playlist.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.common.exception.DuplicatePlaylistException;
import com.playlist.playlist.dto.PlaylistRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Not transactional: each call commits on its own, so the unique constraint really races. */
@SpringBootTest
@ActiveProfiles("test")
class PlaylistRaceIT {

    @Autowired private PlaylistService playlistService;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void simultaneousCreationsOfTheSameName_giveOneSuccessAndOnlyDuplicateErrors() throws Exception {
        User owner = userRepository.save(User.builder()
                .username("race-" + UUID.randomUUID())
                .passwordHash("not-used")
                .enabled(true)
                .build());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            // Arrange
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Callable<Object> call = () -> {
                    start.await();
                    try {
                        return playlistService.crear(new PlaylistRequest("Misma", null, List.of()), owner.getId());
                    } catch (Exception ex) {
                        return ex;
                    }
                };
                futures.add(pool.submit(call));
            }

            // Act
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }

            // Assert: exactly one winner, every loser reported as a duplicate (never a raw DB error)
            long failures = outcomes.stream().filter(o -> o instanceof Exception).count();
            assertThat(outcomes.size() - failures).isEqualTo(1);
            assertThat(outcomes).filteredOn(o -> o instanceof Exception)
                    .allMatch(o -> o instanceof DuplicatePlaylistException);
        } finally {
            pool.shutdownNow();
            playlistRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId()).forEach(playlistRepository::delete);
            userRepository.delete(owner);
        }
    }
}
