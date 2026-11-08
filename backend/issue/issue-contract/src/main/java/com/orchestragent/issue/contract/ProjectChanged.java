package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A project was created, edited, archived or restored. It says only which one; readers fetch it again. */
public record ProjectChanged(UUID eventId, Instant occurredAt, UUID workspaceId, UUID projectId)
        implements DomainEvent {

    public ProjectChanged {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(projectId, "projectId");
    }
}
