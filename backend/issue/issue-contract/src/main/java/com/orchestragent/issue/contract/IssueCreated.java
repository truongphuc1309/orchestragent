package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A new issue exists. {@code creatorType} is the kind of actor that created it; {@code key} is {@code WEB-12}. */
public record IssueCreated(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        UUID projectId,
        UUID issueId,
        String key,
        ActorType creatorType,
        UUID creatorId)
        implements DomainEvent {

    public IssueCreated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(creatorType, "creatorType");
        Objects.requireNonNull(creatorId, "creatorId");
    }
}
