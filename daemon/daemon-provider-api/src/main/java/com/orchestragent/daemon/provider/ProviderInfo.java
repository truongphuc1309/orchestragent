package com.orchestragent.daemon.provider;

import java.util.Objects;

/**
 * What {@link AgentProvider#detect()} found: the version of the tool (empty when it does not say) and where it is,
 * for example the path of the executable (shown by {@code doctor}).
 */
public record ProviderInfo(String version, String location) {

    public ProviderInfo {
        version = version == null ? "" : version.strip();
        location = Objects.requireNonNullElse(location, "");
    }
}
