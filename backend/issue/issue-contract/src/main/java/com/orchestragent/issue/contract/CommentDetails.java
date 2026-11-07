package com.orchestragent.issue.contract;

import java.time.Instant;
import java.util.UUID;

/** A comment as other contexts see it. {@code authorId} is null for system comments. */
public record CommentDetails(
        UUID id, ActorType authorType, UUID authorId, String authorName, String body, Instant createdAt) {
}
