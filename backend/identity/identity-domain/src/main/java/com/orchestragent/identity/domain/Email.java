package com.orchestragent.identity.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/** A normalized (trimmed, lower-cased) email address. Two spellings of the same address are equal. */
public record Email(String value) {

    public static final int MAX_LENGTH = 254;

    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new ValidationException("email.required", "Email is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new ValidationException("email.invalid", "Email address is not valid");
        }
    }

    public static Email of(String raw) {
        return new Email(raw);
    }

    /** The part before the {@code @}, used as the default display name of a new user. */
    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public String toString() {
        return value;
    }
}
