package com.playlist.common;

import com.playlist.common.exception.InvalidTextException;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * Normalizes the input: null stays null, otherwise trim + lowercase (Locale.ROOT).
     * Rejects control characters (\u0000-\u001F, \u007F) with InvalidTextException.
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        assertNoControlChars(input, "input");
        return input.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Throws InvalidTextException if the string contains control characters.
     */
    public static void assertNoControlChars(String input, String fieldName) {
        if (input == null) {
            return;
        }
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= '\u0000' && c <= '\u001F') || c == '\u007F') {
                throw new InvalidTextException(
                        "Field '" + fieldName + "' contains invalid control character at index " + i);
            }
        }
    }
}