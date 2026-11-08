package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The short code of a project, the first part of its issue keys ({@code WEB} in {@code WEB-12}). People choose
 * it; it is upper case, 2 to 10 letters and digits, and starts with a letter. It never changes once the project
 * exists, so an issue key that was copied somewhere (a notification, a work folder) stays true.
 */
public record ProjectKey(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 10;

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9]{" + (MIN_LENGTH - 1) + "," + (MAX_LENGTH - 1) + "}");

    public ProjectKey {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw invalid();
        }
    }

    /** Trims and upper-cases what a person typed, then checks the format. */
    public static ProjectKey parse(String raw) {
        if (raw == null) {
            throw invalid();
        }
        return new ProjectKey(raw.trim().toUpperCase(Locale.ROOT));
    }

    /** True when {@code raw} is already a valid key as it is (no trimming or case change). */
    public static boolean isValid(String raw) {
        return raw != null && FORMAT.matcher(raw).matches();
    }

    private static ValidationException invalid() {
        return new ValidationException(
                "project.invalid_key",
                "A project key is " + MIN_LENGTH + " to " + MAX_LENGTH
                        + " letters and digits and starts with a letter, for example WEB or API2");
    }

    @Override
    public String toString() {
        return value;
    }
}
