package com.playlist.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.common.exception.DuplicatePlaylistException;
import com.playlist.common.exception.PlaylistNotFoundException;
import com.playlist.playlist.dto.PlaylistRequest;
import com.playlist.playlist.dto.PlaylistResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Mock private PlaylistRepository playlistRepository;
    @Mock private UserRepository userRepository;
    @Spy private PlaylistMapper mapper = new PlaylistMapper();
    @InjectMocks private PlaylistService service;

    private final User owner = User.builder().username("demo").passwordHash("x").enabled(true).build();

    @Test
    void create_whenTheNameIsAlreadyTaken_throwsDuplicateWithoutSaving() {
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(owner));
        when(playlistRepository.existsByOwnerIdAndNombreNormalizado(OWNER, "rock")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new PlaylistRequest("  ROCK ", null, List.of()), OWNER))
                .isInstanceOf(DuplicatePlaylistException.class);

        verify(playlistRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_whenAConcurrentRequestWinsTheInsert_throwsDuplicate() {
        // Arrange: the existence check passes, but the unique constraint fires at INSERT time
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(owner));
        when(playlistRepository.existsByOwnerIdAndNombreNormalizado(eq(OWNER), eq("rock"))).thenReturn(false);
        when(playlistRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_playlist_owner_nombre"));

        // Act + Assert: translated inside the service, with the INSERT flushed inside the try
        assertThatThrownBy(() -> service.crear(new PlaylistRequest("Rock", null, List.of()), OWNER))
                .isInstanceOf(DuplicatePlaylistException.class);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void create_storesTheNormalizedName() {
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(owner));
        when(playlistRepository.existsByOwnerIdAndNombreNormalizado(OWNER, "mi lista")).thenReturn(false);
        when(playlistRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlaylistResponse response = service.crear(new PlaylistRequest("  Mi Lista ", "desc", List.of()), OWNER);

        assertThat(response.nombre()).isEqualTo("Mi Lista");
    }

    @Test
    void get_whenMissingOrNotOwned_throwsNotFound() {
        when(playlistRepository.findByOwnerIdAndNombreNormalizado(OWNER, "ajena")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener("Ajena", OWNER)).isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void delete_whenMissingOrNotOwned_throwsNotFoundAndDeletesNothing() {
        when(playlistRepository.findByOwnerIdAndNombreNormalizado(OWNER, "ajena")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar("Ajena", OWNER)).isInstanceOf(PlaylistNotFoundException.class);

        verify(playlistRepository, never()).delete(any());
    }
}
