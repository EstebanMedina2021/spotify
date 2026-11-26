package com.playlist.recommendation;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplates {

    private static final int MAX_CANCIONES = 20;
    private static final int MAX_CHARS_BLOQUE = 2000;

    private final Resource promptResource;
    private String template;
    private String promptVersion;

    public PromptTemplates(@Value("classpath:prompts/recommendations-v1.txt") Resource promptResource) {
        this.promptResource = promptResource;
    }

    @PostConstruct
    void load() throws IOException {
        this.template = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String filename = promptResource.getFilename(); // recommendations-v1.txt
        this.promptVersion = filename.replace("recommendations-", "").replace(".txt", ""); // v1
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String buildPrompt(List<CancionSnapshot> canciones) {
        String bloque = canciones.stream()
                .limit(MAX_CANCIONES)
                .map(c -> String.format("- %s - %s - %s",
                        safe(c.titulo()),
                        safe(c.artista()),
                        safe(c.genero())))
                .collect(Collectors.joining("\n"));

        if (bloque.length() > MAX_CHARS_BLOQUE) {
            bloque = bloque.substring(0, MAX_CHARS_BLOQUE);
        }

        return template.replace("%s", bloque);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "desconocido" : value;
    }
}