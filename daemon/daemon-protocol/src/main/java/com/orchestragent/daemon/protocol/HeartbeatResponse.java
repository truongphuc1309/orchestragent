package com.orchestragent.daemon.protocol;

import java.util.List;
import java.util.UUID;

/** The runtimes whose heartbeat was recorded. An ID missing here is unknown, or not the token owner's. */
public record HeartbeatResponse(List<UUID> runtimeIds) {
}
