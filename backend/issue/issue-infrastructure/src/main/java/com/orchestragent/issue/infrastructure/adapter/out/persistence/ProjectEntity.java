package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of {@code issue.projects}. The {@code next_number} column is deliberately not mapped: it is only ever
 * changed by {@link IssueNumbersAdapter} with one SQL statement, and a mapped copy could write a stale value
 * back when the project is saved in the same transaction.
 */
@Entity
@Table(schema = "issue", name = "projects")
class ProjectEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "key", nullable = false, updatable = false)
    private String projectKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectEntity() {
    }

    ProjectEntity(
            UUID id,
            UUID workspaceId,
            String projectKey,
            String name,
            String description,
            UUID createdBy,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.projectKey = projectKey;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Copies the fields that can change after creation. */
    void update(String name, String description, Instant archivedAt, Instant updatedAt) {
        this.name = name;
        this.description = description;
        this.archivedAt = archivedAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getProjectKey() {
        return projectKey;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
