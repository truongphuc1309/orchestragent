package com.orchestragent.agent.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An agent task changed status (queued, dispatched, running, completed, failed or cancelled). Published for
 * every change, so live views can refresh; {@code status} is the lower-case status code.
 */
public record AgentTaskChanged(
        UUID eventId, Instant occurredAt, UUID workspaceId, UUID issueId, UUID taskId, String status)
        implements DomainEvent {

    public AgentTaskChanged {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
    }
}
