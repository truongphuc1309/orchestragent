package com.orchestragent.issue.infrastructure.adapter.out.agent;

import com.orchestragent.agent.contract.AgentSummary;
import com.orchestragent.issue.application.port.AgentDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Answers the issue context's questions about agents by asking the agent context through its contract. If
 * the agent context is split out, the contract facade becomes an HTTP client and this class stays as it is.
 */
@Component
class ContractAgentDirectory implements AgentDirectory {

    private final com.orchestragent.agent.contract.AgentDirectory agents;

    ContractAgentDirectory(com.orchestragent.agent.contract.AgentDirectory agents) {
        this.agents = agents;
    }

    /** Only active agents can be given work. */
    @Override
    public boolean exists(UUID workspaceId, UUID agentId) {
        return agents.find(workspaceId, agentId).filter(agent -> !agent.archived()).isPresent();
    }

    /** Archived agents keep their name, so issues they were assigned to still show who it was. */
    @Override
    public Map<UUID, String> names(UUID workspaceId, Collection<UUID> agentIds) {
        return agents.findByIds(workspaceId, agentIds).values().stream()
                .collect(Collectors.toMap(AgentSummary::id, AgentSummary::name));
    }
}
