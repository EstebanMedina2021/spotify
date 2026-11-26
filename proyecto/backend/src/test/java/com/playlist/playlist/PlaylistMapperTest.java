package com.playlist.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.playlist.auth.User;
import com.playlist.playlist.dto.CancionRequest;
import com.playlist.playlist.dto.PlaylistRequest;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PlaylistMapperTest {

    private final PlaylistMapper mapper = new PlaylistMapper();
    private final User owner = User.builder().username("demo").passwordHash("x").enabled(true).build();

    @Test
    void normalizedName_doesNotDependOnTheJvmDefaultLocale() {
        // Arrange: in a Turkish locale "I".toLowerCase() is a dotless i, which broke name matching
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            // Act
            Playlist playlist = mapper.toEntity(new PlaylistRequest("ISTANBUL", null, List.of()), owner);

            // Assert
            assertThat(playlist.getNombreNormalizado()).isEqualTo("istanbul");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void entity_keepsTheDisplayNameTrimmedAndNumbersTheSongs() {
        Playlist playlist = mapper.toEntity(new PlaylistRequest("  Mi Lista ", null, List.of(
                new CancionRequest(" A ", " X ", null, null, null),
                new CancionRequest("B", "Y", null, null, null))), owner);

        assertThat(playlist.getNombre()).isEqualTo("Mi Lista");
        assertThat(playlist.getNombreNormalizado()).isEqualTo("mi lista");
        assertThat(playlist.getCanciones()).extracting(Cancion::getPosicion).containsExactly(0, 1);
        assertThat(playlist.getCanciones().get(0).getTitulo()).isEqualTo("A");
    }
}
