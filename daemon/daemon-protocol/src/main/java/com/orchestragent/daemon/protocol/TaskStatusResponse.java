package com.orchestragent.daemon.protocol;

/**
 * The answer to start, messages, status, complete and fail. {@code status} is queued, dispatched, running,
 * completed, failed or cancelled; a daemon that sees {@code cancelled} stops the work.
 */
public record TaskStatusResponse(String status) {
}
