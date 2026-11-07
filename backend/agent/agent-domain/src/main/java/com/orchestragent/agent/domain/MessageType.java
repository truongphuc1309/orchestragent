package com.orchestragent.agent.domain;

import com.orchestragent.shared.error.ValidationException;
import java.util.Locale;

/** What a line of a task's log is. */
public enum MessageType {
    TEXT,
    TOOL_USE,
    TOOL_RESULT,
    ERROR,
    SYSTEM;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MessageType fromCode(String code) {
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new ValidationException("task.invalid_message_type", "Unknown message type: " + code);
        }
    }
}
