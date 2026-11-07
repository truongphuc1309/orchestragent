package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "workspace", name = "members")
class MemberEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Lower-case: owner, admin or member. */
    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemberEntity() {
    }

    MemberEntity(UUID id, UUID workspaceId, UUID userId, String role, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
    }

    void updateRole(String role) {
        this.role = role;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getUserId() {
        return userId;
    }

    String getRole() {
        return role;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
