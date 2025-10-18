package com.vorto.challenge.common;

public final class TextNormalizer {
    private TextNormalizer() {}
    /** Trim, collapse internal whitespace, lower-case (idempotent). */
    public static String normalizeUsername(String s) {
        if (s == null) throw new IllegalArgumentException("username is required");;
        String trimmed = s.trim();
        String collapsed = trimmed.replaceAll("\\s+", " ");
        return collapsed.toLowerCase();
    }
}
