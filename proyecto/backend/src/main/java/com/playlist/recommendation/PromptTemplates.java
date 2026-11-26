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

    static final String PLACEHOLDER = "{{canciones}}";
    private static final int MAX_CANCIONES = 20;
    private static final int MAX_CHARS_BLOQUE = 2000;

    private final Resource promptResource;
    private String template;
    private String promptVersion;

    /**
     * The prompt file is configurable so a new version can be rolled out (and rolled back) without
     * touching code; old versions stay in the repository.
     */
    public PromptTemplates(
            @Value("${app.ai.prompt-file:classpath:prompts/recommendations-v2.txt}") Resource promptResource) {
        this.promptResource = promptResource;
    }

    @PostConstruct
    void load() throws IOException {
        this.template = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!template.contains(PLACEHOLDER)) {
            // Fail at startup instead of silently sending a prompt without the songs.
            throw new IllegalStateException("Prompt template has no " + PLACEHOLDER + " placeholder");
        }
        String filename = promptResource.getFilename(); // recommendations-v2.txt
        this.promptVersion = filename.replace("recommendations-", "").replace(".txt", ""); // v2
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String buildPrompt(List<CancionSnapshot> canciones) {
        String bloque = canciones.stream()
                .limit(MAX_CANCIONES)
                .map(PromptTemplates::toJsonLine)
                .collect(Collectors.joining("\n"));

        if (bloque.length() > MAX_CHARS_BLOQUE) {
            bloque = bloque.substring(0, MAX_CHARS_BLOQUE);
        }

        // replace(), not format(): song data may contain '%' or '{{' and must stay literal data.
        return template.replace(PLACEHOLDER, bloque);
    }

    /**
     * One song as a JSON object. A structured line (instead of "- title - artist - genre") stops small
     * models from copying the separator into the title they answer with, and the quoting keeps song
     * data unambiguous.
     */
    private static String toJsonLine(CancionSnapshot song) {
        return "{\"titulo\":\"" + clean(song.titulo())
                + "\",\"artista\":\"" + clean(song.artista())
                + "\",\"genero\":\"" + clean(song.genero()) + "\"}";
    }

    /** Neutralizes angle brackets and control characters, and escapes what would break the JSON line. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "desconocido";
        }
        return value
                .replace('<', '\u2039').replace('>', '\u203A')
                .replaceAll("\\p{Cntrl}", " ")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
