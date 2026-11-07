package com.orchestragent.agent.contract;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous lookup of agents for other contexts (the issue context uses it to check and name assignees).
 * Becomes an HTTP client if the agent context is split out.
 */
public interface AgentDirectory {

    /** The agent, or empty when the workspace has no agent with this ID (an agent of another workspace counts as none). */
    Optional<AgentSummary> find(UUID workspaceId, UUID agentId);

    /** Looks up many agents of one workspace at once. Unknown IDs are simply missing from the result. */
    Map<UUID, AgentSummary> findByIds(UUID workspaceId, Collection<UUID> agentIds);
}
