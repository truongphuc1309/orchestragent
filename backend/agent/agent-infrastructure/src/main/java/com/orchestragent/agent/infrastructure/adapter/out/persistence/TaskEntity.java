package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "agent", name = "agent_tasks")
class TaskEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "runtime_id", nullable = false)
    private UUID runtimeId;

    /** Lower-case: assigned, comment, changes_requested or retry. */
    @Column(nullable = false)
    private String trigger;

    @Column(name = "trigger_comment_id")
    private UUID triggerCommentId;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    /** Lower-case: queued, dispatched, running, completed, failed or cancelled. */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "work_dir")
    private String workDir;

    private String output;

    private String error;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cost_usd")
    private BigDecimal costUsd;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Makes a write fail when someone else changed the task since it was read. */
    @Version
    private long version;

    protected TaskEntity() {
    }

    TaskEntity(
            UUID id,
            UUID workspaceId,
            UUID issueId,
            UUID agentId,
            UUID runtimeId,
            String trigger,
            UUID triggerCommentId,
            UUID sourceEventId,
            int attempt,
            Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.issueId = issueId;
        this.agentId = agentId;
        this.runtimeId = runtimeId;
        this.trigger = trigger;
        this.triggerCommentId = triggerCommentId;
        this.sourceEventId = sourceEventId;
        this.attempt = attempt;
        this.createdAt = createdAt;
        this.status = "queued";
    }

    /** Copies everything that changes while a task lives. */
    void update(
            UUID runtimeId,
            String status,
            String sessionId,
            String workDir,
            String output,
            String error,
            String failureReason,
            Long inputTokens,
            Long outputTokens,
            BigDecimal costUsd,
            Instant leaseExpiresAt,
            Instant startedAt,
            Instant finishedAt) {
        this.runtimeId = runtimeId;
        this.status = status;
        this.sessionId = sessionId;
        this.workDir = workDir;
        this.output = output;
        this.error = error;
        this.failureReason = failureReason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
        this.leaseExpiresAt = leaseExpiresAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getIssueId() {
        return issueId;
    }

    UUID getAgentId() {
        return agentId;
    }

    UUID getRuntimeId() {
        return runtimeId;
    }

    String getTrigger() {
        return trigger;
    }

    UUID getTriggerCommentId() {
        return triggerCommentId;
    }

    UUID getSourceEventId() {
        return sourceEventId;
    }

    String getStatus() {
        return status;
    }

    int getAttempt() {
        return attempt;
    }

    String getSessionId() {
        return sessionId;
    }

    String getWorkDir() {
        return workDir;
    }

    String getOutput() {
        return output;
    }

    String getError() {
        return error;
    }

    String getFailureReason() {
        return failureReason;
    }

    Long getInputTokens() {
        return inputTokens;
    }

    Long getOutputTokens() {
        return outputTokens;
    }

    BigDecimal getCostUsd() {
        return costUsd;
    }

    Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getFinishedAt() {
        return finishedAt;
    }
}
