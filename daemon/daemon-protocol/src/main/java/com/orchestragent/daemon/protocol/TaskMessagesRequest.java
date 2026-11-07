package com.orchestragent.daemon.protocol;

import java.util.List;

/** {@code POST /api/daemon/tasks/{id}/messages}: a batch of at most 200 lines. */
public record TaskMessagesRequest(List<TaskMessage> messages) {
}
