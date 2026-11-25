package com.playlist.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void normalize_trimsAndLowercases() {
        assertThat(TextNormalizer.normalize("  Hello World  ")).isEqualTo("hello world");
        assertThat(TextNormalizer.normalize("ÁLBUM")).isEqualTo("álbum");
    }

    @Test
    void normalize_nullReturnsNull() {
        assertThat(TextNormalizer.normalize(null)).isNull();
    }

    @Test
    void normalize_rejectsControlCharacters() {
        assertThatThrownBy(() -> TextNormalizer.normalize("hello\u0000world"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control character");

        assertThatThrownBy(() -> TextNormalizer.normalize("bad\u007Fchar"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertNoControlChars_allowsNormalText() {
        TextNormalizer.assertNoControlChars("Normal text 123", "field");
        TextNormalizer.assertNoControlChars(null, "field");
    }

    @Test
    void assertNoControlChars_rejectsControl() {
        assertThatThrownBy(() -> TextNormalizer.assertNoControlChars("a\tb", "nombre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }
}