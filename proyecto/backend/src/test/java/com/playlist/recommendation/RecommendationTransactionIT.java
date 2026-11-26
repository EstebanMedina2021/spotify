package com.playlist.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.playlist.PlaylistRepository;
import com.playlist.playlist.PlaylistService;
import com.playlist.playlist.dto.CancionRequest;
import com.playlist.playlist.dto.PlaylistRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The test itself is NOT transactional: it must observe the real transactional behavior of the
 * service. A database connection must not be held while the AI provider is being called.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecommendationTransactionIT {

    @Autowired private RecommendationService recommendationService;
    @Autowired private PlaylistService playlistService;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private AiRecommendationClient aiClient;

    @Test
    void providerIsCalledOutsideAnyDatabaseTransaction() {
        User owner = userRepository.save(User.builder()
                .username("tx-probe-" + UUID.randomUUID())
                .passwordHash("not-used")
                .enabled(true)
                .build());
        try {
            // Arrange
            playlistService.crear(new PlaylistRequest("Rock", null,
                    List.of(new CancionRequest("Bohemian Rhapsody", "Queen", null, null, "rock"))), owner.getId());
            AtomicReference<Boolean> transactionActiveDuringCall = new AtomicReference<>();
            when(aiClient.recommend(any(), any())).thenAnswer(invocation -> {
                transactionActiveDuringCall.set(TransactionSynchronizationManager.isActualTransactionActive());
                return CompletableFuture.completedFuture(RecommendationResult.ok(
                        List.of(new RecomendacionItem("Under Pressure", "Queen", "rock", "x")), "MOCK", "v1"));
            });

            // Act
            recommendationService.recomendar("Rock", owner.getId());

            // Assert
            assertThat(transactionActiveDuringCall.get()).isFalse();
        } finally {
            playlistRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId()).forEach(playlistRepository::delete);
            userRepository.delete(owner);
        }
    }
}
