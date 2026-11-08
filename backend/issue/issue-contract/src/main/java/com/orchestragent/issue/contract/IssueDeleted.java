package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An issue is gone. {@code key} ({@code WEB-12}) is what people knew it by. */
public record IssueDeleted(UUID eventId, Instant occurredAt, UUID workspaceId, UUID issueId, String key)
        implements DomainEvent {

    public IssueDeleted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(key, "key");
    }
}
