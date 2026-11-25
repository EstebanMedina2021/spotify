package com.playlist.playlist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    Optional<Playlist> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Playlist> findByOwnerIdAndNombreNormalizado(UUID ownerId, String nombreNormalizado);

    List<Playlist> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByOwnerIdAndNombreNormalizado(UUID ownerId, String nombreNormalizado);
}