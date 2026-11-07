package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentRepository;
import com.orchestragent.agent.domain.RuntimeId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AgentRepositoryAdapter implements AgentRepository {

    /** An ID no agent has, for "any agent" in the name check (a null parameter confuses PostgreSQL). */
    private static final UUID NO_AGENT = new UUID(0, 0);

    private final AgentJpaRepository jpa;

    AgentRepositoryAdapter(AgentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Agent agent) {
        jpa.findById(agent.id().value()).ifPresentOrElse(
                existing -> existing.update(
                        agent.name(), agent.instructions(), agent.runtimeId().value(), agent.model(), agent.archivedAt()),
                () -> jpa.save(new AgentEntity(
                        agent.id().value(),
                        agent.workspaceId(),
                        agent.name(),
                        agent.instructions(),
                        agent.runtimeId().value(),
                        agent.model(),
                        agent.createdBy(),
                        agent.archivedAt(),
                        agent.createdAt())));
    }

    @Override
    public Optional<Agent> find(UUID workspaceId, AgentId id) {
        return jpa.findByIdAndWorkspaceId(id.value(), workspaceId).map(AgentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Agent> findByWorkspace(UUID workspaceId, boolean includeArchived) {
        return jpa.findByWorkspace(workspaceId, includeArchived).stream().map(AgentRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Agent> findByIds(UUID workspaceId, Collection<AgentId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByWorkspaceIdAndIdIn(workspaceId, ids.stream().map(AgentId::value).toList()).stream()
                .map(AgentRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean activeNameExists(UUID workspaceId, String name, AgentId except) {
        return jpa.activeNameExists(workspaceId, name, except == null ? NO_AGENT : except.value());
    }

    private static Agent toDomain(AgentEntity entity) {
        return new Agent(
                new AgentId(entity.getId()),
                entity.getWorkspaceId(),
                entity.getName(),
                entity.getInstructions(),
                new RuntimeId(entity.getRuntimeId()),
                entity.getModel(),
                entity.getCreatedBy(),
                entity.getArchivedAt(),
                entity.getCreatedAt());
    }
}
