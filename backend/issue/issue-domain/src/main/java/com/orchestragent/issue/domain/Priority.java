package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Locale;

/** How urgent an issue is, from least to most. */
public enum Priority {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Priority fromCode(String code) {
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new ValidationException("issue.invalid_priority", "Unknown priority: " + code);
        }
    }
}
