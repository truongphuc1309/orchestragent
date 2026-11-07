package com.orchestragent.daemon.protocol;

/**
 * {@code POST /api/daemon/tasks/{id}/complete}. {@code output} is the agent's final answer, which becomes a
 * comment on the issue. {@code sessionId} and {@code workDir} let a later run resume. Repeating the call is
 * harmless, so a daemon may retry it.
 */
public record CompleteRequest(String output, String sessionId, String workDir, TaskUsage usage) {
}
