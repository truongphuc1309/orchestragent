package com.orchestragent.agent.domain;

import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;

/**
 * One line of the log of a task: text the agent wrote, a tool it called, the result, an error. {@code seq}
 * numbers the lines of a task from 0 in the order the daemon produced them; storing a {@code seq} twice is a
 * no-op, so a daemon can safely resend a batch.
 */
public record TaskMessage(long seq, MessageType type, String content, Instant createdAt) {

    /** Longer content is cut: a tool result can be a whole file, and the log is for reading, not for archiving. */
    public static final int MAX_CONTENT_LENGTH = 50_000;

    static final String TRUNCATED = "\n… (cut: too long)";

    public TaskMessage {
        if (seq < 0) {
            throw new ValidationException("task.invalid_message_seq", "Message numbers start at 0");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        content = content == null ? "" : content;
    }

    /** Builds a message from daemon input, cutting overlong content. */
    public static TaskMessage received(long seq, MessageType type, String content, Instant now) {
        String text = content == null ? "" : content;
        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH - TRUNCATED.length()) + TRUNCATED;
        }
        return new TaskMessage(seq, type, text, now);
    }
}
