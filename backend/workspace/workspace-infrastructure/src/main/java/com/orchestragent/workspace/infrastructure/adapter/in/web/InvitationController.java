package com.orchestragent.workspace.infrastructure.adapter.in.web;

import com.orchestragent.platform.security.CurrentUser;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.workspace.application.InvitationService;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.InvitationResponse;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.InviteRequest;
import com.orchestragent.workspace.infrastructure.adapter.in.web.WorkspaceDtos.WorkspaceResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Invitations managed from inside a workspace, and the signed-in user's own invitations. */
@RestController
class InvitationController {

    private final InvitationService invitations;

    InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/api/workspaces/{workspaceId}/invitations")
    List<InvitationResponse> listForWorkspace(CurrentUser user, @PathVariable UUID workspaceId) {
        return invitations.listForWorkspace(user.id(), workspaceId).stream().map(InvitationResponse::from).toList();
    }

    @PostMapping("/api/workspaces/{workspaceId}/invitations")
    ResponseEntity<InvitationResponse> invite(
            CurrentUser user, @PathVariable UUID workspaceId, @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvitationResponse.from(
                        invitations.invite(user.id(), workspaceId, request.email(), request.parsedRole())));
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/invitations/{invitationId}")
    ResponseEntity<Void> revoke(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID invitationId) {
        invitations.revoke(user.id(), workspaceId, invitationId);
        return ResponseEntity.noContent().build();
    }

    /** Invitations addressed to the email address of the signed-in user. */
    @GetMapping("/api/invitations")
    List<InvitationResponse> mine(CurrentUser user) {
        return invitations.listMine(requireEmail(user)).stream().map(InvitationResponse::from).toList();
    }

    @PostMapping("/api/invitations/{invitationId}/accept")
    WorkspaceResponse accept(CurrentUser user, @PathVariable UUID invitationId) {
        return WorkspaceResponse.from(invitations.accept(user.id(), requireEmail(user), invitationId));
    }

    /** Invitations belong to an email address, which only a browser session (JWT) carries. */
    private static String requireEmail(CurrentUser user) {
        if (user.email() == null || user.email().isBlank()) {
            throw new ForbiddenException("invitation.email_required", "Invitations need a signed-in user with an email");
        }
        return user.email();
    }
}
