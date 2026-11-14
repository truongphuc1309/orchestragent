package com.orchestragent.identity.domain;

import com.orchestragent.shared.error.ValidationException;

/**
 * "About you": optional free text, at most 500 characters. Line breaks are kept (Windows line breaks become
 * {@code \n}); other control characters are refused. Null and blank become the empty bio.
 */
public record Bio(String value) {

    public static final int MAX_LENGTH = 500;

    public Bio {
        value = value == null ? "" : value.replace("\r\n", "\n").strip();
        if (value.length() > MAX_LENGTH) {
            throw new ValidationException("profile.bio_too_long", "About you must be at most " + MAX_LENGTH + " characters");
        }
        if (value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new ValidationException("profile.bio_invalid", "About you must not contain control characters");
        }
    }

    public static Bio of(String raw) {
        return new Bio(raw);
    }
}
