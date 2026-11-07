package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A message on an issue, written by a member, an agent or the system. */
public final class Comment {

    public static final int MAX_BODY_LENGTH = 10_000;

    private final CommentId id;
    private final UUID workspaceId;
    private final IssueId issueId;
    private final ActorType authorType;
    private final UUID authorId;
    private final String body;
    private final Instant createdAt;
    private final UUID sourceEventId;

    public Comment(
            CommentId id,
            UUID workspaceId,
            IssueId issueId,
            ActorType authorType,
            UUID authorId,
            String body,
            Instant createdAt) {
        this(id, workspaceId, issueId, authorType, authorId, body, createdAt, null);
    }

    public Comment(
            CommentId id,
            UUID workspaceId,
            IssueId issueId,
            ActorType authorType,
            UUID authorId,
            String body,
            Instant createdAt,
            UUID sourceEventId) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.issueId = Objects.requireNonNull(issueId, "issueId");
        this.authorType = Objects.requireNonNull(authorType, "authorType");
        if ((authorType == ActorType.SYSTEM) != (authorId == null)) {
            throw new IllegalArgumentException("Only system comments have no author ID");
        }
        this.authorId = authorId;
        this.body = Objects.requireNonNull(body, "body");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.sourceEventId = sourceEventId;
    }

    public static Comment write(
            UUID workspaceId, IssueId issueId, ActorType authorType, UUID authorId, String body, Instant now) {
        return new Comment(CommentId.generate(), workspaceId, issueId, authorType, authorId, validBody(body), now);
    }

    /**
     * A comment an event caused (the result of an agent, a failure notice). It remembers the event, so
     * handling the same event twice writes it once.
     */
    public static Comment writeFor(
            UUID sourceEventId,
            UUID workspaceId,
            IssueId issueId,
            ActorType authorType,
            UUID authorId,
            String body,
            Instant now) {
        return new Comment(
                CommentId.generate(), workspaceId, issueId, authorType, authorId, validBody(body), now,
                Objects.requireNonNull(sourceEventId, "sourceEventId"));
    }

    public static String validBody(String rawBody) {
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty() || body.length() > MAX_BODY_LENGTH) {
            throw new ValidationException(
                    "comment.invalid_body", "A comment must be 1 to " + MAX_BODY_LENGTH + " characters");
        }
        return body;
    }

    public CommentId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public IssueId issueId() {
        return issueId;
    }

    public ActorType authorType() {
        return authorType;
    }

    /** The member or agent ID, or null for a system comment. */
    public UUID authorId() {
        return authorId;
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** The event that caused this comment, or null for a comment someone wrote. */
    public UUID sourceEventId() {
        return sourceEventId;
    }
}
