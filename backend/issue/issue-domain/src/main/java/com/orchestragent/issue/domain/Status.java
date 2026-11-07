package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Locale;

/** Where an issue is in its life. The codes are the lower-case names used in the database and the API. */
public enum Status {
    BACKLOG,
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    BLOCKED,
    CANCELLED;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True for statuses that end the work: nothing more is expected on the issue. */
    public boolean isClosed() {
        return this == DONE || this == CANCELLED;
    }

    public static Status fromCode(String code) {
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new ValidationException("issue.invalid_status", "Unknown status: " + code);
        }
    }
}
