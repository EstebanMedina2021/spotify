package com.playlist.playlist;

import com.playlist.auth.User;
import com.playlist.auth.UserRepository;
import com.playlist.common.TextNormalizer;
import com.playlist.common.exception.DuplicatePlaylistException;
import com.playlist.common.exception.PlaylistNotFoundException;
import com.playlist.playlist.dto.PlaylistRequest;
import com.playlist.playlist.dto.PlaylistResponse;
import com.playlist.playlist.dto.PlaylistSummary;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final PlaylistMapper mapper;

    @Transactional
    public PlaylistResponse crear(PlaylistRequest request, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + ownerId));

        String nombreNormalizado = TextNormalizer.normalize(request.nombre());
        if (playlistRepository.existsByOwnerIdAndNombreNormalizado(ownerId, nombreNormalizado)) {
            throw new DuplicatePlaylistException(request.nombre());
        }

        Playlist playlist = mapper.toEntity(request, owner);
        try {
            Playlist saved = playlistRepository.save(playlist);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatePlaylistException(request.nombre());
        }
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummary> listar(UUID ownerId) {
        return playlistRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(mapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistResponse obtener(String nombre, UUID ownerId) {
        String nombreNormalizado = TextNormalizer.normalize(nombre);
        Playlist playlist = playlistRepository
                .findByOwnerIdAndNombreNormalizado(ownerId, nombreNormalizado)
                .orElseThrow(() -> new PlaylistNotFoundException(nombre));
        return mapper.toResponse(playlist);
    }

    @Transactional
    public void eliminar(String nombre, UUID ownerId) {
        String nombreNormalizado = TextNormalizer.normalize(nombre);
        Playlist playlist = playlistRepository
                .findByOwnerIdAndNombreNormalizado(ownerId, nombreNormalizado)
                .orElseThrow(() -> new PlaylistNotFoundException(nombre));
        playlistRepository.delete(playlist);
    }
}