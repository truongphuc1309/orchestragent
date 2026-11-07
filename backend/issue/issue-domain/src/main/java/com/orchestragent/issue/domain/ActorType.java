package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Locale;

/** The kind of actor behind an assignment, an issue or a comment. */
public enum ActorType {
    MEMBER,
    AGENT,
    SYSTEM;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ActorType fromCode(String code) {
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new ValidationException("issue.invalid_actor", "Unknown actor type: " + code);
        }
    }
}
