package com.orchestragent.agent.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository {

    /** Inserts a new agent or updates an existing one. */
    void save(Agent agent);

    Optional<Agent> find(UUID workspaceId, AgentId id);

    /** Agents of a workspace by name; archived ones only when asked for. */
    List<Agent> findByWorkspace(UUID workspaceId, boolean includeArchived);

    List<Agent> findByIds(UUID workspaceId, Collection<AgentId> ids);

    /** True when an active agent of the workspace already has this name (ignoring case), other than {@code except}. */
    boolean activeNameExists(UUID workspaceId, String name, AgentId except);
}
