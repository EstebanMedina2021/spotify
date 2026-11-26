package com.playlist.recommendation;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FallbackCatalog {

    private static final Map<String, List<RecomendacionItem>> CATALOG = Map.of(
            "rock", List.of(
                    new RecomendacionItem("Bohemian Rhapsody", "Queen", "rock", "Clásico atemporal"),
                    new RecomendacionItem("Stairway to Heaven", "Led Zeppelin", "rock", "Épico de los 70"),
                    new RecomendacionItem("Smells Like Teen Spirit", "Nirvana", "rock", "Grunge icónico")
            ),
            "pop", List.of(
                    new RecomendacionItem("Blinding Lights", "The Weeknd", "pop", "Synth-pop moderno"),
                    new RecomendacionItem("Levitating", "Dua Lipa", "pop", "Pop disco"),
                    new RecomendacionItem("As It Was", "Harry Styles", "pop", "Pop alternativo")
            ),
            "jazz", List.of(
                    new RecomendacionItem("So What", "Miles Davis", "jazz", "Modal jazz"),
                    new RecomendacionItem("Take Five", "Dave Brubeck", "jazz", "Jazz clásico"),
                    new RecomendacionItem("My Favorite Things", "John Coltrane", "jazz", "Soprano sax")
            ),
            "electronic", List.of(
                    new RecomendacionItem("Strobe", "deadmau5", "electronic", "Progressive house"),
                    new RecomendacionItem("Around the World", "Daft Punk", "electronic", "French house"),
                    new RecomendacionItem("Windowlicker", "Aphex Twin", "electronic", "IDM")
            ),
            "hip-hop", List.of(
                    new RecomendacionItem("Lose Yourself", "Eminem", "hip-hop", "Rap motivacional"),
                    new RecomendacionItem("N.Y. State of Mind", "Nas", "hip-hop", "East coast clásico"),
                    new RecomendacionItem("Alright", "Kendrick Lamar", "hip-hop", "Hip-hop consciente")
            ),
            "classical", List.of(
                    new RecomendacionItem("Claro de Luna", "Beethoven", "classical", "Sonata para piano"),
                    new RecomendacionItem("Las cuatro estaciones", "Vivaldi", "classical", "Barroco"),
                    new RecomendacionItem("Nocturno Op. 9 No. 2", "Chopin", "classical", "Romanticismo")
            ),
            "latin", List.of(
                    new RecomendacionItem("Despacito", "Luis Fonsi", "latin", "Reggaetón"),
                    new RecomendacionItem("La Bamba", "Ritchie Valens", "latin", "Rock latino"),
                    new RecomendacionItem("Bailando", "Enrique Iglesias", "latin", "Pop latino")
            ),
            "metal", List.of(
                    new RecomendacionItem("Master of Puppets", "Metallica", "metal", "Thrash metal"),
                    new RecomendacionItem("Paranoid", "Black Sabbath", "metal", "Heavy metal clásico"),
                    new RecomendacionItem("Chop Suey!", "System of a Down", "metal", "Nu metal")
            ),
            "r&b", List.of(
                    new RecomendacionItem("Blame It", "Jamie Foxx", "r&b", "R&B contemporáneo"),
                    new RecomendacionItem("Adorn", "Miguel", "r&b", "Neo-soul"),
                    new RecomendacionItem("Redbone", "Childish Gambino", "r&b", "Funk-soul")
            ),
            "country", List.of(
                    new RecomendacionItem("Jolene", "Dolly Parton", "country", "Country clásico"),
                    new RecomendacionItem("Ring of Fire", "Johnny Cash", "country", "Country outlaw"),
                    new RecomendacionItem("Cruise", "Florida Georgia Line", "country", "Bro-country")
            )
    );

    private static final String DEFAULT_GENERO = "pop";

    public List<RecomendacionItem> suggestionsFor(String genero) {
        if (genero == null || genero.isBlank()) {
            return CATALOG.get(DEFAULT_GENERO);
        }
        return CATALOG.getOrDefault(genero.toLowerCase(), CATALOG.get(DEFAULT_GENERO));
    }

    public String firstGenre(List<CancionSnapshot> canciones) {
        if (canciones == null) {
            return DEFAULT_GENERO;
        }
        return canciones.stream()
                .map(CancionSnapshot::genero)
                .filter(g -> g != null && !g.isBlank())
                .findFirst()
                .map(String::toLowerCase)
                .orElse(DEFAULT_GENERO);
    }
}