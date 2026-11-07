package com.orchestragent.agent.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A daemon started working on a task. The issue context moves the issue to "in progress". */
public record AgentTaskStarted(
        UUID eventId, Instant occurredAt, UUID workspaceId, UUID issueId, UUID taskId, UUID agentId)
        implements DomainEvent {

    public AgentTaskStarted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(agentId, "agentId");
    }
}
