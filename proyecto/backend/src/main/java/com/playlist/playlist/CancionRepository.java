package com.playlist.playlist;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancionRepository extends JpaRepository<Cancion, UUID> {
}