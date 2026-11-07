package com.orchestragent.inbox.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The inbox of one person in one workspace changed (a notification arrived, was read or went away). The live
 * updates tell only that person's browsers.
 */
public record InboxChanged(UUID eventId, Instant occurredAt, UUID workspaceId, UUID recipientId) implements DomainEvent {

    public InboxChanged {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(recipientId, "recipientId");
    }
}
