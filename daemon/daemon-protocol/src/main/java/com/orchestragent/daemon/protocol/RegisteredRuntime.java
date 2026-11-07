package com.orchestragent.daemon.protocol;

import java.util.UUID;

/** A runtime the server created or revived for one provider of the daemon. {@code status} is online or offline. */
public record RegisteredRuntime(
        UUID id, UUID workspaceId, String provider, String providerVersion, String deviceName, String status) {
}
