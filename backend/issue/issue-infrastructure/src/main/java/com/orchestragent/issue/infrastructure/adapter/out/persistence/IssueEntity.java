package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "issue", name = "issues")
class IssueEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    /** Lower-case code, for example in_progress. */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String priority;

    @Column(name = "assignee_type")
    private String assigneeType;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "creator_type", nullable = false)
    private String creatorType;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(nullable = false)
    private double position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IssueEntity() {
    }

    IssueEntity(
            UUID id,
            UUID workspaceId,
            int number,
            String title,
            String description,
            String status,
            String priority,
            String assigneeType,
            UUID assigneeId,
            String creatorType,
            UUID creatorId,
            double position,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.number = number;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.assigneeType = assigneeType;
        this.assigneeId = assigneeId;
        this.creatorType = creatorType;
        this.creatorId = creatorId;
        this.position = position;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Copies the fields that can change after creation. */
    void update(
            String title,
            String description,
            String status,
            String priority,
            String assigneeType,
            UUID assigneeId,
            double position,
            Instant updatedAt) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.assigneeType = assigneeType;
        this.assigneeId = assigneeId;
        this.position = position;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    int getNumber() {
        return number;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    String getStatus() {
        return status;
    }

    String getPriority() {
        return priority;
    }

    String getAssigneeType() {
        return assigneeType;
    }

    UUID getAssigneeId() {
        return assigneeId;
    }

    String getCreatorType() {
        return creatorType;
    }

    UUID getCreatorId() {
        return creatorId;
    }

    double getPosition() {
        return position;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
