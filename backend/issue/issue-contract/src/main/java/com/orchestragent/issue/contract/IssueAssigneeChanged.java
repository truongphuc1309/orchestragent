package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The assignee of an issue changed. A null type and id means "unassigned" (before or after). The assignee
 * type is {@code MEMBER} or {@code AGENT}, never {@code SYSTEM}.
 */
public record IssueAssigneeChanged(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        UUID issueId,
        ActorType previousAssigneeType,
        UUID previousAssigneeId,
        ActorType newAssigneeType,
        UUID newAssigneeId,
        ActorType actorType,
        UUID actorId)
        implements DomainEvent {

    public IssueAssigneeChanged {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
    }
}
