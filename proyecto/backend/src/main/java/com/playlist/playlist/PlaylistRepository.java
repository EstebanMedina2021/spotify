package com.playlist.playlist;

import com.playlist.playlist.dto.PlaylistSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    Optional<Playlist> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Playlist> findByOwnerIdAndNombreNormalizado(UUID ownerId, String nombreNormalizado);

    List<Playlist> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByOwnerIdAndNombreNormalizado(UUID ownerId, String nombreNormalizado);

    /**
     * Summaries for the list endpoint in a single query: the song count is computed by the database,
     * so no playlist's lazy {@code canciones} collection is loaded (avoids one extra query per row).
     */
    @Query("""
            select new com.playlist.playlist.dto.PlaylistSummary(p.id, p.nombre, p.descripcion, size(p.canciones))
            from Playlist p
            where p.owner.id = :ownerId
            order by p.createdAt desc""")
    List<PlaylistSummary> findSummariesByOwnerId(@Param("ownerId") UUID ownerId);
}