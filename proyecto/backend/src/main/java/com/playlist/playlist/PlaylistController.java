package com.playlist.playlist;

import com.playlist.playlist.dto.PlaylistRequest;
import com.playlist.playlist.dto.PlaylistResponse;
import com.playlist.playlist.dto.PlaylistSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/lists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    @PostMapping
    public ResponseEntity<PlaylistResponse> crear(
            @Valid @RequestBody PlaylistRequest request,
            Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        PlaylistResponse creada = playlistService.crear(request, ownerId);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{nombre}")
                .buildAndExpand(creada.nombre())
                .encode()
                .toUri();

        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping
    public List<PlaylistSummary> listar(Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        return playlistService.listar(ownerId);
    }

    @GetMapping("/{listName}")
    public PlaylistResponse obtener(
            @PathVariable String listName,
            Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        return playlistService.obtener(listName, ownerId);
    }

    @DeleteMapping("/{listName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable String listName,
            Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        playlistService.eliminar(listName, ownerId);
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("No autenticado");
        }
        return UUID.fromString(jwt.getSubject());
    }
}