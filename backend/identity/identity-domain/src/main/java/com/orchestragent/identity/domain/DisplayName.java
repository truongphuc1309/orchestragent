package com.orchestragent.identity.domain;

import com.orchestragent.shared.error.ValidationException;

/** The name other people see. Trimmed, 1 to 100 characters, on one line. */
public record DisplayName(String value) {

    public static final int MAX_LENGTH = 100;

    public DisplayName {
        if (value == null || value.isBlank()) {
            throw new ValidationException("profile.name_required", "Name is required");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new ValidationException("profile.name_too_long", "Name must be at most " + MAX_LENGTH + " characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("profile.name_invalid", "Name must not contain line breaks or control characters");
        }
    }

    public static DisplayName of(String raw) {
        return new DisplayName(raw);
    }
}
