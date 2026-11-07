package com.orchestragent.daemon.protocol;

import java.util.List;
import java.util.UUID;

/** {@code POST /api/daemon/heartbeat} and {@code POST /api/daemon/deregister}. */
public record RuntimeIdsRequest(List<UUID> runtimeIds) {
}
