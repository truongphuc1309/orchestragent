package com.orchestragent.agent.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** New log messages of a running task were stored; {@code lastSeq} is the highest sequence number now stored. */
public record AgentTaskMessagesAppended(
        UUID eventId, Instant occurredAt, UUID workspaceId, UUID issueId, UUID taskId, long lastSeq)
        implements DomainEvent {

    public AgentTaskMessagesAppended {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(taskId, "taskId");
    }
}
