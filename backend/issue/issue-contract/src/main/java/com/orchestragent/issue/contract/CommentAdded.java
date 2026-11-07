package com.orchestragent.issue.contract;

import com.orchestragent.shared.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A comment was added. Only comments written by a person may start agent work; {@code authorType} lets
 * listeners tell them apart. {@code authorId} is null for {@code SYSTEM} comments.
 */
public record CommentAdded(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        UUID issueId,
        UUID commentId,
        ActorType authorType,
        UUID authorId)
        implements DomainEvent {

    public CommentAdded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(authorType, "authorType");
    }
}
