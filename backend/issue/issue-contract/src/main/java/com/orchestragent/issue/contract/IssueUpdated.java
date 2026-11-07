package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Fields of an issue changed. {@code changedFields} names them with the API's field names (title,
 * description, status, priority, position). {@code status} is the status after the change. The assignee has
 * its own event, {@link IssueAssigneeChanged}.
 */
public record IssueUpdated(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        UUID issueId,
        Set<String> changedFields,
        String status,
        ActorType actorType,
        UUID actorId)
        implements DomainEvent {

    public IssueUpdated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
        changedFields = Set.copyOf(changedFields);
    }
}
