package com.orchestragent.workspace.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "workspace", name = "invitations")
class InvitationEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String email;

    /** Lower-case: owner, admin or member. */
    @Column(nullable = false)
    private String role;

    /** Lower-case: pending, accepted or revoked. */
    @Column(nullable = false)
    private String status;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvitationEntity() {
    }

    InvitationEntity(
            UUID id, UUID workspaceId, String email, String role, String status, UUID invitedBy, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.email = email;
        this.role = role;
        this.status = status;
        this.invitedBy = invitedBy;
        this.createdAt = createdAt;
    }

    void updateStatus(String status) {
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getEmail() {
        return email;
    }

    String getRole() {
        return role;
    }

    String getStatus() {
        return status;
    }

    UUID getInvitedBy() {
        return invitedBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
