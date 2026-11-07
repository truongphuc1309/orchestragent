package com.orchestragent.daemon.protocol;

/** An AI tool the daemon found on its computer, such as {@code claude}, with its version (may be empty). */
public record ProviderInfo(String provider, String version) {
}
