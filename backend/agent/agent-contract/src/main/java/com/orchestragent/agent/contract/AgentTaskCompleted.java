package com.orchestragent.agent.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A task finished successfully. {@code output} is the agent's final answer; the issue context turns it into
 * a comment of the agent and moves the issue to review.
 */
public record AgentTaskCompleted(
        UUID eventId, Instant occurredAt, UUID workspaceId, UUID issueId, UUID taskId, UUID agentId, String output)
        implements DomainEvent {

    public AgentTaskCompleted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(agentId, "agentId");
        output = output == null ? "" : output;
    }
}
