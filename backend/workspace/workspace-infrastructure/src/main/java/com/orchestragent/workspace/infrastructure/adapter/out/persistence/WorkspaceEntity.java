package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "workspace", name = "workspaces")
class WorkspaceEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceEntity() {
    }

    WorkspaceEntity(UUID id, String name, String slug, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getSlug() {
        return slug;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
