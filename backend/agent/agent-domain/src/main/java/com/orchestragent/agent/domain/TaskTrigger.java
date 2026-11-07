package com.orchestragent.agent.domain;

import java.util.Locale;

/** Why a task exists. */
public enum TaskTrigger {
    /** The issue was assigned to the agent. */
    ASSIGNED,
    /** A person commented on an issue that is assigned to the agent. */
    COMMENT,
    /** A reviewer sent the issue back from review with a comment. */
    CHANGES_REQUESTED,
    /** A person retried a failed task. */
    RETRY;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TaskTrigger fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
