package com.orchestragent.agent.domain;

import java.util.Locale;

/**
 * Where a task is. A task waits ({@code QUEUED}), is handed to a daemon ({@code DISPATCHED}, with a lease the
 * daemon must act on in time), runs, and ends as {@code COMPLETED}, {@code FAILED} or {@code CANCELLED}.
 */
public enum TaskStatus {
    QUEUED,
    DISPATCHED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** The lower-case code used in the database and the API. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TaskStatus fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }

    /** True while the task still needs attention: it can be cancelled, and it blocks a second run on its issue. */
    public boolean isActive() {
        return this == QUEUED || this == DISPATCHED || this == RUNNING;
    }

    public boolean isFinished() {
        return !isActive();
    }
}
