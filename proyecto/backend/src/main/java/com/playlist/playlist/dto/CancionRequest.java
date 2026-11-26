package com.playlist.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancionRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 100, message = "El título no puede superar 100 caracteres")
        String titulo,

        @NotBlank(message = "El artista es obligatorio")
        @Size(max = 100, message = "El artista no puede superar 100 caracteres")
        String artista,

        @Size(max = 100, message = "El álbum no puede superar 100 caracteres")
        String album,

        @Size(max = 10, message = "El año no puede superar 10 caracteres")
        String anno,

        @Size(max = 50, message = "El género no puede superar 50 caracteres")
        String genero
) {
}