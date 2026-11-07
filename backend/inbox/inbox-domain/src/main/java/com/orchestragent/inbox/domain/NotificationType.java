package com.orchestragent.inbox.domain;

import java.util.Locale;

/** Why someone gets a notification. */
public enum NotificationType {
    /** Someone else assigned an issue to them. */
    ISSUE_ASSIGNED,
    /** An agent finished work on an issue they created: the issue waits for review. */
    AGENT_COMPLETED,
    /** An agent could not finish work on an issue they created: the issue is blocked. */
    AGENT_FAILED;

    /** The code in the database and the API: {@code issue_assigned}, {@code agent_completed}, {@code agent_failed}. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NotificationType fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
