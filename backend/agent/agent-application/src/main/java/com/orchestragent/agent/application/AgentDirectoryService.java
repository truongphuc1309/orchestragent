package com.orchestragent.agent.application;

import com.orchestragent.agent.contract.AgentDirectory;
import com.orchestragent.agent.contract.AgentSummary;
import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The agent context's answer to other contexts. It never leaks an agent of another workspace. */
@Service
class AgentDirectoryService implements AgentDirectory {

    private final AgentRepository agents;

    AgentDirectoryService(AgentRepository agents) {
        this.agents = agents;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentSummary> find(UUID workspaceId, UUID agentId) {
        return agents.find(workspaceId, new AgentId(agentId)).map(AgentDirectoryService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AgentSummary> findByIds(UUID workspaceId, Collection<UUID> agentIds) {
        return agents.findByIds(workspaceId, agentIds.stream().map(AgentId::new).toList()).stream()
                .collect(Collectors.toMap(agent -> agent.id().value(), AgentDirectoryService::summary));
    }

    private static AgentSummary summary(Agent agent) {
        return new AgentSummary(agent.id().value(), agent.workspaceId(), agent.name(), agent.isArchived());
    }
}
