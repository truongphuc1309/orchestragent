package com.orchestragent.daemon.protocol;

/**
 * One line of a task's log. {@code seq} numbers the lines of a task from 0, in order; sending a number the
 * server already has is harmless. {@code type} is text, tool_use, tool_result, error or system.
 */
public record TaskMessage(long seq, String type, String content) {
}
