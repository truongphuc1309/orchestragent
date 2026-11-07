package com.orchestragent.daemon.provider;

import java.util.Objects;

/**
 * How a run ended. A run succeeded when {@code error} is null: {@code output} is the agent's final answer, which
 * becomes a comment on the issue. A failed run has an {@code error} (shown to people) and a {@code failureReason}
 * (a short code such as {@code agent_error} or {@code timeout}). {@code sessionId} is the tool's session, kept so a
 * later run can resume it, in both cases.
 */
public record RunResult(String output, String sessionId, Usage usage, String error, String failureReason) {

    /** The reason of a failure that the provider did not explain further. */
    public static final String AGENT_ERROR = "agent_error";

    /** The run took longer than {@link RunRequest#timeout()}. */
    public static final String TIMEOUT = "timeout";

    public RunResult {
        output = Objects.requireNonNullElse(output, "");
        usage = Objects.requireNonNullElse(usage, Usage.NONE);
        if (error != null && (failureReason == null || failureReason.isBlank())) {
            failureReason = AGENT_ERROR;
        }
        if (error == null) {
            failureReason = null;
        }
    }

    public static RunResult succeeded(String output, String sessionId, Usage usage) {
        return new RunResult(output, sessionId, usage, null, null);
    }

    public static RunResult failed(String error, String failureReason, String sessionId, Usage usage) {
        String message = error == null || error.isBlank() ? "The run failed without saying why" : error;
        return new RunResult("", sessionId, usage, message, failureReason);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
