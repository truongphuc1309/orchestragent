package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TaskJpaRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<TaskEntity> findByWorkspaceIdAndIssueIdOrderByCreatedAtDescIdDesc(UUID workspaceId, UUID issueId, Pageable page);

    List<TaskEntity> findByIssueIdAndStatusIn(UUID issueId, Collection<String> statuses);

    List<TaskEntity> findByAgentIdAndStatusIn(UUID agentId, Collection<String> statuses);

    boolean existsByIssueIdAndStatus(UUID issueId, String status);

    boolean existsBySourceEventId(UUID sourceEventId);

    Optional<TaskEntity> findFirstByIssueIdAndAgentIdAndStatusAndSessionIdIsNotNullOrderByFinishedAtDesc(
            UUID issueId, UUID agentId, String status);

    List<TaskEntity> findByStatusAndLeaseExpiresAtBefore(String status, Instant cutoff);

    List<TaskEntity> findByStatusAndRuntimeIdIn(String status, Collection<UUID> runtimeIds);

    @Query("""
            select t from TaskEntity t
            where t.status = 'running'
              and t.runtimeId in (select r.id from RuntimeEntity r where r.status = 'offline')
            """)
    List<TaskEntity> findRunningOnOfflineRuntimes();
}
