package com.orchestragent.workspace.application;

import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

/** Read models returned by the use cases. */
public final class Views {

    private Views() {
    }

    /** A workspace as seen by one of its members: {@code role} is that member's own role. */
    public record WorkspaceView(UUID id, String name, String slug, WorkspaceRole role, Instant createdAt) {
    }

    public record MemberView(UUID userId, String name, String email, WorkspaceRole role, Instant joinedAt) {
    }

    public record InvitationView(
            UUID id,
            UUID workspaceId,
            String workspaceName,
            String email,
            WorkspaceRole role,
            String invitedByName,
            Instant createdAt) {
    }
}
