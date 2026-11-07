package com.orchestragent.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A user's membership in a workspace. The user lives in the identity context and is referenced by ID only. */
public final class Member {

    private final MemberId id;
    private final WorkspaceId workspaceId;
    private final UUID userId;
    private final Instant createdAt;
    private Role role;

    public Member(MemberId id, WorkspaceId workspaceId, UUID userId, Role role, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.role = Objects.requireNonNull(role, "role");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Member join(WorkspaceId workspaceId, UUID userId, Role role, Instant now) {
        return new Member(MemberId.generate(), workspaceId, userId, role, now);
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "newRole");
    }

    public MemberId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public UUID userId() {
        return userId;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
