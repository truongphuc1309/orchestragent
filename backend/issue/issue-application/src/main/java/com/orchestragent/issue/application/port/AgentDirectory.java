package com.orchestragent.issue.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Lookup of agents, which belong to another context. Until the agent context exists (step 7) the adapter
 * answers "no such agent", so issues can only be assigned to members. Later it calls the agent contract.
 */
public interface AgentDirectory {

    /** True when the workspace has an agent with this ID that can still be assigned work. */
    boolean exists(UUID workspaceId, UUID agentId);

    /** Display names of agents; unknown IDs are missing from the result. */
    Map<UUID, String> names(UUID workspaceId, Collection<UUID> agentIds);
}
