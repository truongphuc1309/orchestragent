package com.orchestragent.workspace.application;

import com.orchestragent.identity.contract.UserDirectory;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.application.Views.InvitationView;
import com.orchestragent.workspace.application.Views.WorkspaceView;
import com.orchestragent.workspace.application.port.InvitationNotifier;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Email;
import com.orchestragent.workspace.domain.Invitation;
import com.orchestragent.workspace.domain.InvitationId;
import com.orchestragent.workspace.domain.InvitationRepository;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.MemberPolicy;
import com.orchestragent.workspace.domain.MemberRepository;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.Workspace;
import com.orchestragent.workspace.domain.WorkspaceId;
import com.orchestragent.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inviting people by email and accepting invitations. An invitation can only be accepted by a signed-in user
 * whose own email address is the invited one.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final WorkspaceRepository workspaces;
    private final UserDirectory users;
    private final InvitationNotifier notifier;
    private final Clock clock;

    public InvitationService(
            InvitationRepository invitations,
            MemberRepository members,
            WorkspaceRepository workspaces,
            UserDirectory users,
            InvitationNotifier notifier,
            Clock clock) {
        this.invitations = invitations;
        this.members = members;
        this.workspaces = workspaces;
        this.users = users;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public InvitationView invite(UUID actorId, UUID workspaceId, String rawEmail, WorkspaceRole role) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        Member actor = requireMember(id, actorId);
        Role invitedRole = Roles.toDomain(role);
        if (!MemberPolicy.canInvite(actor.role(), invitedRole)) {
            throw new ForbiddenException("invitation.forbidden", "You are not allowed to invite people with this role");
        }
        Email email = Email.of(rawEmail);

        boolean alreadyMember = users.findByEmail(email.value())
                .flatMap(user -> members.find(id, user.id()))
                .isPresent();
        if (alreadyMember) {
            throw new ConflictException("invitation.already_member", "This person is already a member");
        }
        if (invitations.findPending(id, email).isPresent()) {
            throw new ConflictException("invitation.already_pending", "This person has already been invited");
        }

        Workspace workspace = workspaces.findById(id).orElseThrow(WorkspaceService::notFound);
        Invitation invitation = Invitation.issue(id, email, invitedRole, actorId, clock.instant());
        invitations.save(invitation);

        String inviterName = nameOf(actorId);
        notifier.invited(new InvitationNotifier.Notice(
                email.value(), workspace.name(), inviterName, invitation.id().value(), role.name().toLowerCase(Locale.ROOT)));
        return toView(invitation, workspace.name(), inviterName);
    }

    /** Pending invitations of a workspace, visible to admins and owners only. */
    @Transactional(readOnly = true)
    public List<InvitationView> listForWorkspace(UUID actorId, UUID workspaceId) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        requireManager(id, actorId);
        String workspaceName = workspaces.findById(id).orElseThrow(WorkspaceService::notFound).name();
        return invitations.findPendingByWorkspace(id).stream()
                .map(invitation -> toView(invitation, workspaceName, nameOf(invitation.invitedBy())))
                .toList();
    }

    @Transactional
    public void revoke(UUID actorId, UUID workspaceId, UUID invitationId) {
        WorkspaceId id = new WorkspaceId(workspaceId);
        requireManager(id, actorId);
        Invitation invitation = invitations.findById(new InvitationId(invitationId))
                .filter(candidate -> candidate.workspaceId().equals(id))
                .orElseThrow(InvitationService::invitationNotFound);
        invitation.revoke();
        invitations.save(invitation);
    }

    /** Invitations addressed to the signed-in user's email address. */
    @Transactional(readOnly = true)
    public List<InvitationView> listMine(String userEmail) {
        return invitations.findPendingByEmail(Email.of(userEmail)).stream()
                .map(invitation -> toView(
                        invitation,
                        workspaces.findById(invitation.workspaceId()).map(Workspace::name).orElse("Unknown workspace"),
                        nameOf(invitation.invitedBy())))
                .toList();
    }

    /**
     * Joins the workspace. Anyone else's invitation, a revoked one and an unknown one all look the same
     * ("not found"), so invitation IDs reveal nothing.
     */
    @Transactional
    public WorkspaceView accept(UUID userId, String userEmail, UUID invitationId) {
        Email email = Email.of(userEmail);
        Invitation invitation = invitations.findById(new InvitationId(invitationId))
                .filter(candidate -> candidate.isPending() && candidate.email().equals(email))
                .orElseThrow(InvitationService::invitationNotFound);
        Workspace workspace =
                workspaces.findById(invitation.workspaceId()).orElseThrow(InvitationService::invitationNotFound);

        Role role = members.find(invitation.workspaceId(), userId)
                .map(Member::role)
                .orElseGet(() -> {
                    members.save(Member.join(invitation.workspaceId(), userId, invitation.role(), clock.instant()));
                    return invitation.role();
                });
        invitation.accept();
        invitations.save(invitation);
        return WorkspaceService.toView(workspace, role);
    }

    private Member requireMember(WorkspaceId workspaceId, UUID userId) {
        return members.find(workspaceId, userId).orElseThrow(WorkspaceService::notFound);
    }

    private void requireManager(WorkspaceId workspaceId, UUID userId) {
        Member actor = requireMember(workspaceId, userId);
        if (!MemberPolicy.canManageInvitations(actor.role())) {
            throw new ForbiddenException("invitation.forbidden", "Only admins and owners can manage invitations");
        }
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(UserView::name).orElse("Unknown user");
    }

    private static InvitationView toView(Invitation invitation, String workspaceName, String invitedByName) {
        return new InvitationView(
                invitation.id().value(),
                invitation.workspaceId().value(),
                workspaceName,
                invitation.email().value(),
                Roles.toContract(invitation.role()),
                invitedByName,
                invitation.createdAt());
    }

    private static NotFoundException invitationNotFound() {
        return new NotFoundException("invitation.not_found", "Invitation not found");
    }
}
