package com.orchestragent.issue.infrastructure.adapter.out.agent;

import com.orchestragent.issue.application.port.AgentDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Placeholder until the agent context exists (step 7): no agent exists, so issues can only be assigned to
 * members. Step 7 replaces this class with a call to the agent contract.
 */
@Component
class NoAgentDirectory implements AgentDirectory {

    @Override
    public boolean exists(UUID workspaceId, UUID agentId) {
        return false;
    }

    @Override
    public Map<UUID, String> names(UUID workspaceId, Collection<UUID> agentIds) {
        return Map.of();
    }
}
