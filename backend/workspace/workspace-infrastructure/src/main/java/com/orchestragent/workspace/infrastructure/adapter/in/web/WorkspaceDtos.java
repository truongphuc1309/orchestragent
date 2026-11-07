package com.orchestragent.workspace.infrastructure.adapter.in.web;

import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.application.Views.InvitationView;
import com.orchestragent.workspace.application.Views.MemberView;
import com.orchestragent.workspace.application.Views.WorkspaceView;
import com.orchestragent.workspace.contract.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Wire format of the workspace REST API. Roles travel as lower-case strings: owner, admin, member. */
final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    record CreateWorkspaceRequest(@NotBlank @Size(max = 60) String name) {
    }

    record WorkspaceResponse(UUID id, String name, String slug, String role, Instant createdAt) {

        static WorkspaceResponse from(WorkspaceView view) {
            return new WorkspaceResponse(view.id(), view.name(), view.slug(), roleCode(view.role()), view.createdAt());
        }
    }

    record MemberResponse(UUID userId, String name, String email, String role, Instant joinedAt) {

        static MemberResponse from(MemberView view) {
            return new MemberResponse(view.userId(), view.name(), view.email(), roleCode(view.role()), view.joinedAt());
        }
    }

    record ChangeRoleRequest(@NotNull String role) {

        WorkspaceRole parsedRole() {
            return parseRole(role);
        }
    }

    record InviteRequest(@NotBlank @Size(max = 254) String email, @NotNull String role) {

        WorkspaceRole parsedRole() {
            return parseRole(role);
        }
    }

    record InvitationResponse(
            UUID id,
            UUID workspaceId,
            String workspaceName,
            String email,
            String role,
            String invitedByName,
            Instant createdAt) {

        static InvitationResponse from(InvitationView view) {
            return new InvitationResponse(
                    view.id(),
                    view.workspaceId(),
                    view.workspaceName(),
                    view.email(),
                    roleCode(view.role()),
                    view.invitedByName(),
                    view.createdAt());
        }
    }

    static String roleCode(WorkspaceRole role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    static WorkspaceRole parseRole(String code) {
        try {
            return WorkspaceRole.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ValidationException("role.invalid", "Role must be one of: owner, admin, member");
        }
    }
}
