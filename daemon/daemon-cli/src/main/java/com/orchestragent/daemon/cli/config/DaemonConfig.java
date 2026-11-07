package com.orchestragent.daemon.cli.config;

import java.net.URI;
import java.util.Objects;

/**
 * What {@code login} saved: the server, the API token and this computer's daemon ID, chosen once and kept so the
 * server recognises the computer after a restart. {@link #toString()} hides the token.
 */
public record DaemonConfig(URI server, String token, String daemonId) {

    public DaemonConfig {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(daemonId, "daemonId");
    }

    @Override
    public String toString() {
        return "DaemonConfig[server=" + server + ", token=(hidden), daemonId=" + daemonId + "]";
    }
}
