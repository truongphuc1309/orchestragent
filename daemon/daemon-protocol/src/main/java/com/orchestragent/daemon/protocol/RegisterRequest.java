package com.orchestragent.daemon.protocol;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/daemon/register}. {@code daemonId} is chosen once by the daemon and kept, so registering
 * again after a restart finds the same runtimes.
 */
public record RegisterRequest(String daemonId, String deviceName, UUID workspaceId, List<ProviderInfo> providers) {
}
