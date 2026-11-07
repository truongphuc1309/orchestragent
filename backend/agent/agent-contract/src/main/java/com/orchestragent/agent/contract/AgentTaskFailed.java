package com.orchestragent.agent.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A task failed. {@code failureReason} is a short code (agent_error, timeout, runtime_offline...) and
 * {@code error} the explanation; the issue context writes a system comment and moves the issue to blocked.
 */
public record AgentTaskFailed(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        UUID issueId,
        UUID taskId,
        UUID agentId,
        String failureReason,
        String error)
        implements DomainEvent {

    public AgentTaskFailed {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(agentId, "agentId");
        failureReason = failureReason == null ? "agent_error" : failureReason;
        error = error == null ? "" : error;
    }
}
