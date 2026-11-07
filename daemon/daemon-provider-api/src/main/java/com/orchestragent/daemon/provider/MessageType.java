package com.orchestragent.daemon.provider;

import java.util.Locale;

/** The kind of a log line, as the server knows them. */
public enum MessageType {
    TEXT,
    TOOL_USE,
    TOOL_RESULT,
    ERROR,
    SYSTEM;

    /** The wire code: {@code text}, {@code tool_use}, {@code tool_result}, {@code error} or {@code system}. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
