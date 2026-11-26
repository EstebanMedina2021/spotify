package com.playlist.recommendation;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Cleans what the LLM returns. The answer is untrusted and small models make predictable mistakes:
 * repeating songs the user already has and gluing the artist onto the title.
 */
final class RecommendationFilter {

    private RecommendationFilter() {
    }

    /** Items that are not already in the playlist, with the artist stripped from the title if glued on. */
    static List<RecomendacionItem> novelItems(List<RecomendacionItem> items, List<CancionSnapshot> known) {
        return items.stream()
                .map(RecommendationFilter::withCleanTitle)
                .filter(item -> known.stream().noneMatch(song -> isSameSong(item, song)))
                .toList();
    }

    private static RecomendacionItem withCleanTitle(RecomendacionItem item) {
        String title = item.titulo();
        String artist = item.artista();
        if (title != null && artist != null) {
            // "Levitating - Dua Lipa" with artist "Dua Lipa" -> "Levitating"
            String suffix = " - " + artist.trim();
            if (title.length() > suffix.length() && title.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                title = title.substring(0, title.length() - suffix.length()).trim();
            }
        }
        return new RecomendacionItem(title, artist, item.genero(), item.razon());
    }

    /** Same title and an overlapping artist (so a cover by someone else is a different song). */
    private static boolean isSameSong(RecomendacionItem item, CancionSnapshot song) {
        if (!normalize(item.titulo()).equals(normalize(song.titulo()))) {
            return false;
        }
        String a = normalize(item.artista());
        String b = normalize(song.artista());
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
