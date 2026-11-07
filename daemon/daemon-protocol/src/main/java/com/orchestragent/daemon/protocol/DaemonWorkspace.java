package com.orchestragent.daemon.protocol;

import java.util.UUID;

/** A workspace the owner of the API token belongs to: where the daemon may register. */
public record DaemonWorkspace(UUID id, String name, String slug) {
}
