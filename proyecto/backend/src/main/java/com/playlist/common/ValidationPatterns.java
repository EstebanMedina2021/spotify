package com.playlist.common;

/** Shared Bean Validation constants (annotation attributes must be compile-time constants). */
public final class ValidationPatterns {

    /** Same character set TextNormalizer rejects: ASCII control characters (\x00-\x1F, \x7F). */
    public static final String NO_CONTROL_CHARS = "^[^\\p{Cntrl}]*$";

    public static final String NO_CONTROL_CHARS_MESSAGE = "contiene caracteres no permitidos";

    private ValidationPatterns() {
    }
}
