package com.orchestragent.workspace.domain;

import com.orchestragent.shared.error.ConflictException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An invitation of an email address to join a workspace with a role. */
public final class Invitation {

    private final InvitationId id;
    private final WorkspaceId workspaceId;
    private final Email email;
    private final Role role;
    private final UUID invitedBy;
    private final Instant createdAt;
    private InvitationStatus status;

    public Invitation(
            InvitationId id,
            WorkspaceId workspaceId,
            Email email,
            Role role,
            InvitationStatus status,
            UUID invitedBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.email = Objects.requireNonNull(email, "email");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.invitedBy = Objects.requireNonNull(invitedBy, "invitedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Invitation issue(WorkspaceId workspaceId, Email email, Role role, UUID invitedBy, Instant now) {
        return new Invitation(
                InvitationId.generate(), workspaceId, email, role, InvitationStatus.PENDING, invitedBy, now);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public void accept() {
        requirePending();
        status = InvitationStatus.ACCEPTED;
    }

    public void revoke() {
        requirePending();
        status = InvitationStatus.REVOKED;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new ConflictException("invitation.not_pending", "This invitation is no longer pending");
        }
    }

    public InvitationId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public Email email() {
        return email;
    }

    public Role role() {
        return role;
    }

    public InvitationStatus status() {
        return status;
    }

    public UUID invitedBy() {
        return invitedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
