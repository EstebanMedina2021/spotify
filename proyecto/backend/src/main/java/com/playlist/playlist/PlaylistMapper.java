package com.playlist.playlist;

import com.playlist.auth.User;
import com.playlist.playlist.dto.CancionRequest;
import com.playlist.playlist.dto.CancionResponse;
import com.playlist.playlist.dto.PlaylistRequest;
import com.playlist.playlist.dto.PlaylistResponse;
import com.playlist.playlist.dto.PlaylistSummary;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

    public Playlist toEntity(PlaylistRequest request, User owner) {
        Playlist playlist = Playlist.builder()
                .nombre(request.nombre().trim())
                .nombreNormalizado(request.nombre().trim().toLowerCase())
                .descripcion(request.descripcion())
                .owner(owner)
                .build();

        List<Cancion> canciones = new ArrayList<>();
        if (request.canciones() != null) {
            int posicion = 0;
            for (CancionRequest c : request.canciones()) {
                Cancion cancion = Cancion.builder()
                        .titulo(c.titulo().trim())
                        .artista(c.artista().trim())
                        .album(c.album())
                        .anno(c.anno())
                        .genero(c.genero())
                        .posicion(posicion++)
                        .playlist(playlist)
                        .build();
                canciones.add(cancion);
            }
        }
        playlist.setCanciones(canciones);
        return playlist;
    }

    public PlaylistResponse toResponse(Playlist playlist) {
        List<CancionResponse> canciones = playlist.getCanciones() == null
                ? List.of()
                : playlist.getCanciones().stream()
                        .map(this::toCancionResponse)
                        .toList();

        return new PlaylistResponse(
                playlist.getId(),
                playlist.getNombre(),
                playlist.getDescripcion(),
                canciones,
                playlist.getCreatedAt(),
                playlist.getUpdatedAt()
        );
    }

    public PlaylistSummary toSummary(Playlist playlist) {
        int numero = playlist.getCanciones() == null ? 0 : playlist.getCanciones().size();
        return new PlaylistSummary(
                playlist.getId(),
                playlist.getNombre(),
                playlist.getDescripcion(),
                numero
        );
    }

    private CancionResponse toCancionResponse(Cancion cancion) {
        return new CancionResponse(
                cancion.getId(),
                cancion.getTitulo(),
                cancion.getArtista(),
                cancion.getAlbum(),
                cancion.getAnno(),
                cancion.getGenero()
        );
    }
}