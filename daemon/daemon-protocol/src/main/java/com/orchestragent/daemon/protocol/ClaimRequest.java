package com.orchestragent.daemon.protocol;

import java.util.List;
import java.util.UUID;

/** {@code POST /api/daemon/tasks/claim}: ask for up to {@code maxTasks} waiting tasks of these runtimes. */
public record ClaimRequest(List<UUID> runtimeIds, int maxTasks) {
}
