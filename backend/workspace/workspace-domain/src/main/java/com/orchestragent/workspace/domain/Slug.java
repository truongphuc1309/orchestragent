package com.orchestragent.workspace.domain;

import java.text.Normalizer;
import java.util.Locale;

/** URL-safe workspace identifier such as {@code acme-team}, derived from the workspace name. */
public final class Slug {

    public static final int MAX_LENGTH = 40;
    static final String FALLBACK = "workspace";

    private Slug() {
    }

    /** Lower-case ASCII letters and digits separated by single dashes; accents are dropped. */
    public static String fromName(String name) {
        String folded = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        String dashed = folded.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (dashed.length() > MAX_LENGTH) {
            dashed = dashed.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return dashed.isEmpty() ? FALLBACK : dashed;
    }

    /** The first attempt is the plain slug; later attempts append {@code -2}, {@code -3}... */
    public static String candidate(String base, int attempt) {
        return attempt <= 1 ? base : base + "-" + attempt;
    }
}
