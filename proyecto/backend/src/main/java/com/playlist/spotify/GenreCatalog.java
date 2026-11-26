package com.playlist.spotify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Curated, versioned genre list used when Spotify's genre endpoint is not available. */
@Component
public class GenreCatalog {

    static final String RESOURCE = "spotify/genres-fallback-v1.json";

    private final List<String> genres;

    public GenreCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.genres = List.copyOf(objectMapper.readValue(in, new TypeReference<List<String>>() { }));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load " + RESOURCE, ex);
        }
    }

    public List<String> genres() {
        return genres;
    }
}
