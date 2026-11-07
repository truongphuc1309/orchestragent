package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "issue", name = "comments")
class CommentEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    /** Lower-case: member, agent or system. */
    @Column(name = "author_type", nullable = false)
    private String authorType;

    /** Null for system comments. */
    @Column(name = "author_id")
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommentEntity() {
    }

    CommentEntity(
            UUID id, UUID workspaceId, UUID issueId, String authorType, UUID authorId, String body, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.issueId = issueId;
        this.authorType = authorType;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getIssueId() {
        return issueId;
    }

    String getAuthorType() {
        return authorType;
    }

    UUID getAuthorId() {
        return authorId;
    }

    String getBody() {
        return body;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
