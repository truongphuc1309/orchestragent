package com.orchestragent.daemon.provider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * One run of a tool.
 *
 * @param prompt what to do, built by the daemon from the agent, the issue and its comments
 * @param workDir the folder the tool works in; it exists. A resumed session runs in the folder of the earlier run
 * @param model the model to ask the tool for, or null for the tool's default
 * @param priorSessionId the tool's session to continue, or null to start a new one
 * @param timeout the longest the run may take; after that the provider stops the tool and fails the run
 */
public record RunRequest(String prompt, Path workDir, String model, String priorSessionId, Duration timeout) {

    public RunRequest {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(workDir, "workDir");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        model = blankToNull(model);
        priorSessionId = blankToNull(priorSessionId);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
