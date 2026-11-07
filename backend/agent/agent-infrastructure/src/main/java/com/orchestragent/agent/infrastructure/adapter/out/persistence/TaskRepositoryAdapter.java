package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.TaskId;
import com.orchestragent.agent.domain.TaskRepository;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskTrigger;
import com.orchestragent.agent.domain.TaskUsage;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TaskRepositoryAdapter implements TaskRepository {

    private static final List<String> ACTIVE = List.of("queued", "dispatched", "running");

    /**
     * Hands out waiting tasks in one statement. {@code FOR UPDATE SKIP LOCKED} locks the rows it picks and
     * skips rows another claim has locked, so two daemons polling at the same moment never receive the same
     * task; the picked rows are marked dispatched in the same statement, before the locks are released. A task
     * is not picked while another task of its issue is dispatched or running, so one issue is never worked on
     * twice at once. Tasks of archived agents are not picked.
     */
    private static final String CLAIM = """
            WITH picked AS (
                SELECT t.id
                FROM agent.agent_tasks t
                JOIN agent.agents a ON a.id = t.agent_id AND a.archived_at IS NULL
                WHERE t.status = 'queued'
                  AND t.runtime_id IN (:runtimeIds)
                  AND NOT EXISTS (SELECT 1 FROM agent.agent_tasks r
                                  WHERE r.issue_id = t.issue_id AND r.status IN ('dispatched', 'running'))
                ORDER BY t.created_at, t.id
                LIMIT :max
                FOR UPDATE OF t SKIP LOCKED
            )
            UPDATE agent.agent_tasks t
            SET status = 'dispatched', lease_expires_at = :lease, version = t.version + 1
            FROM picked
            WHERE t.id = picked.id
            RETURNING t.id
            """;

    private final TaskJpaRepository jpa;
    private final JdbcClient jdbc;

    TaskRepositoryAdapter(TaskJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public void save(AgentTask task) {
        jpa.findById(task.id().value()).ifPresentOrElse(
                existing -> existing.update(
                        task.runtimeId().value(),
                        task.status().code(),
                        task.sessionId(),
                        task.workDir(),
                        task.output(),
                        task.error(),
                        task.failureReason(),
                        task.usage().inputTokens(),
                        task.usage().outputTokens(),
                        task.usage().costUsd(),
                        task.leaseExpiresAt(),
                        task.startedAt(),
                        task.finishedAt()),
                () -> {
                    TaskEntity entity = new TaskEntity(
                            task.id().value(),
                            task.workspaceId(),
                            task.issueId(),
                            task.agentId().value(),
                            task.runtimeId().value(),
                            task.trigger().code(),
                            task.triggerCommentId(),
                            task.sourceEventId(),
                            task.attempt(),
                            task.createdAt());
                    entity.update(
                            task.runtimeId().value(),
                            task.status().code(),
                            task.sessionId(),
                            task.workDir(),
                            task.output(),
                            task.error(),
                            task.failureReason(),
                            task.usage().inputTokens(),
                            task.usage().outputTokens(),
                            task.usage().costUsd(),
                            task.leaseExpiresAt(),
                            task.startedAt(),
                            task.finishedAt());
                    jpa.save(entity);
                });
    }

    @Override
    public Optional<AgentTask> find(UUID workspaceId, TaskId id) {
        return jpa.findByIdAndWorkspaceId(id.value(), workspaceId).map(TaskRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<AgentTask> findById(TaskId id) {
        return jpa.findById(id.value()).map(TaskRepositoryAdapter::toDomain);
    }

    @Override
    public List<AgentTask> findByIssue(UUID workspaceId, UUID issueId, int limit) {
        return jpa.findByWorkspaceIdAndIssueIdOrderByCreatedAtDescIdDesc(workspaceId, issueId, PageRequest.of(0, limit))
                .stream()
                .map(TaskRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<AgentTask> findActiveByIssue(UUID issueId) {
        return jpa.findByIssueIdAndStatusIn(issueId, ACTIVE).stream().map(TaskRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<AgentTask> findActiveByAgent(AgentId agentId) {
        return jpa.findByAgentIdAndStatusIn(agentId.value(), ACTIVE).stream().map(TaskRepositoryAdapter::toDomain).toList();
    }

    @Override
    public boolean existsQueuedForIssue(UUID issueId) {
        return jpa.existsByIssueIdAndStatus(issueId, TaskStatus.QUEUED.code());
    }

    @Override
    public boolean existsBySourceEvent(UUID eventId) {
        return jpa.existsBySourceEventId(eventId);
    }

    @Override
    public Optional<AgentTask> findLastResumable(UUID issueId, AgentId agentId) {
        return jpa.findFirstByIssueIdAndAgentIdAndStatusAndSessionIdIsNotNullOrderByFinishedAtDesc(
                        issueId, agentId.value(), TaskStatus.COMPLETED.code())
                .map(TaskRepositoryAdapter::toDomain);
    }

    @Override
    public List<AgentTask> claim(Collection<RuntimeId> runtimeIds, int max, Instant now) {
        if (runtimeIds.isEmpty()) {
            return List.of();
        }
        List<UUID> claimedIds = jdbc.sql(CLAIM)
                .param("runtimeIds", runtimeIds.stream().map(RuntimeId::value).toList())
                .param("max", max)
                .param("lease", OffsetDateTime.ofInstant(now.plus(AgentTask.LEASE), ZoneOffset.UTC))
                .query(UUID.class)
                .list();
        // Loaded after the update, so each entity already carries the new status and version.
        return jpa.findAllById(claimedIds).stream()
                .sorted(Comparator.comparing(TaskEntity::getCreatedAt).thenComparing(TaskEntity::getId))
                .map(TaskRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<AgentTask> findExpiredDispatched(Instant now) {
        return jpa.findByStatusAndLeaseExpiresAtBefore(TaskStatus.DISPATCHED.code(), now).stream()
                .map(TaskRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<AgentTask> findRunningOnOfflineRuntimes() {
        return jpa.findRunningOnOfflineRuntimes().stream().map(TaskRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<AgentTask> findRunningOnRuntimes(Collection<RuntimeId> runtimeIds) {
        if (runtimeIds.isEmpty()) {
            return List.of();
        }
        return jpa.findByStatusAndRuntimeIdIn(
                        TaskStatus.RUNNING.code(), runtimeIds.stream().map(RuntimeId::value).toList())
                .stream()
                .map(TaskRepositoryAdapter::toDomain)
                .toList();
    }

    private static AgentTask toDomain(TaskEntity entity) {
        return new AgentTask(
                new TaskId(entity.getId()),
                entity.getWorkspaceId(),
                entity.getIssueId(),
                new AgentId(entity.getAgentId()),
                new RuntimeId(entity.getRuntimeId()),
                TaskTrigger.fromCode(entity.getTrigger()),
                entity.getTriggerCommentId(),
                entity.getSourceEventId(),
                entity.getAttempt(),
                TaskStatus.fromCode(entity.getStatus()),
                entity.getSessionId(),
                entity.getWorkDir(),
                entity.getOutput(),
                entity.getError(),
                entity.getFailureReason(),
                new TaskUsage(entity.getInputTokens(), entity.getOutputTokens(), entity.getCostUsd()),
                entity.getLeaseExpiresAt(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt());
    }
}
