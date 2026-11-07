package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AgentJpaRepository extends JpaRepository<AgentEntity, UUID> {

    Optional<AgentEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<AgentEntity> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    @Query("""
            select a from AgentEntity a
            where a.workspaceId = :workspaceId and (:includeArchived = true or a.archivedAt is null)
            order by lower(a.name), a.id
            """)
    List<AgentEntity> findByWorkspace(
            @Param("workspaceId") UUID workspaceId, @Param("includeArchived") boolean includeArchived);

    @Query("""
            select count(a) > 0 from AgentEntity a
            where a.workspaceId = :workspaceId and a.archivedAt is null
              and lower(a.name) = lower(:name) and a.id <> :except
            """)
    boolean activeNameExists(
            @Param("workspaceId") UUID workspaceId, @Param("name") String name, @Param("except") UUID except);
}
