package com.orchestragent.daemon.protocol;

/**
 * {@code POST /api/daemon/tasks/{id}/fail}. {@code failureReason} is a short code such as {@code agent_error}
 * or {@code timeout}. Repeating the call is harmless, so a daemon may retry it.
 */
public record FailRequest(String error, String failureReason, String sessionId, String workDir) {
}
