package com.playlist.spotify;

import com.playlist.spotify.dto.SpotifyGenresResponse;
import com.playlist.spotify.dto.SpotifySearchResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/spotify")
@RequiredArgsConstructor
public class SpotifyController {

    private final SpotifyService spotifyService;

    @GetMapping("/genres")
    public SpotifyGenresResponse genres() {
        return spotifyService.listGenres();
    }

    @GetMapping("/search")
    public SpotifySearchResponse search(
            @RequestParam
            @NotBlank(message = "no puede estar vacío")
            @Size(max = 100, message = "máximo 100 caracteres")
            @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "contiene caracteres no permitidos")
            String q,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "mínimo 1")
            @Max(value = 10, message = "máximo 10")
            int limit) {
        return spotifyService.search(q, limit);
    }
}
