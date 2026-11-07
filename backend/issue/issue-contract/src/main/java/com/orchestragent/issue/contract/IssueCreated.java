package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A new issue exists. {@code creatorType} is the kind of actor that created it. */
public record IssueCreated(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        UUID issueId,
        int number,
        ActorType creatorType,
        UUID creatorId)
        implements DomainEvent {

    public IssueCreated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(creatorType, "creatorType");
        Objects.requireNonNull(creatorId, "creatorId");
    }
}
