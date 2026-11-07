package com.orchestragent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.application.Fakes.CapturingNotifier;
import com.orchestragent.workspace.application.Fakes.FakeUsers;
import com.orchestragent.workspace.application.Fakes.InMemoryInvitations;
import com.orchestragent.workspace.application.Fakes.InMemoryMembers;
import com.orchestragent.workspace.application.Fakes.InMemoryWorkspaces;
import com.orchestragent.workspace.application.Views.InvitationView;
import com.orchestragent.workspace.application.Views.WorkspaceView;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationServiceTest {

    private final InMemoryMembers members = new InMemoryMembers();
    private final InMemoryWorkspaces workspaces = new InMemoryWorkspaces(members);
    private final InMemoryInvitations invitations = new InMemoryInvitations();
    private final FakeUsers users = new FakeUsers();
    private final CapturingNotifier notifier = new CapturingNotifier();
    private final WorkspaceService workspaceService = new WorkspaceService(workspaces, members, Fakes.clock());
    private final InvitationService service =
            new InvitationService(invitations, members, workspaces, users, notifier, Fakes.clock());

    private final UserView owner = users.add("Olivia", "olivia@example.com");
    private final UserView admin = users.add("Adam", "adam@example.com");
    private final UserView member = users.add("Mia", "mia@example.com");
    private final UserView invitee = users.add("Ivy", "ivy@example.com");
    private final WorkspaceView acme = workspaceService.create(owner.id(), "Acme");
    private final UUID workspace = acme.id();

    {
        members.save(Member.join(new WorkspaceId(workspace), admin.id(), Role.ADMIN, Fakes.clock().instant()));
        members.save(Member.join(new WorkspaceId(workspace), member.id(), Role.MEMBER, Fakes.clock().instant()));
    }

    @Test
    void ownerInvitesAnEmailAndTheInviteeIsNotified() {
        InvitationView view = service.invite(owner.id(), workspace, "  IVY@example.com ", WorkspaceRole.MEMBER);

        assertThat(view.email()).isEqualTo("ivy@example.com");
        assertThat(view.workspaceName()).isEqualTo("Acme");
        assertThat(view.invitedByName()).isEqualTo("Olivia");
        assertThat(notifier.sent).singleElement().satisfies(notice -> {
            assertThat(notice.recipientEmail()).isEqualTo("ivy@example.com");
            assertThat(notice.workspaceName()).isEqualTo("Acme");
            assertThat(notice.inviterName()).isEqualTo("Olivia");
            assertThat(notice.invitationId()).isEqualTo(view.id());
            assertThat(notice.role()).isEqualTo("member");
        });
    }

    @Test
    void peopleWithNoAccountYetCanBeInvitedToo() {
        service.invite(owner.id(), workspace, "newcomer@example.com", WorkspaceRole.MEMBER);

        assertThat(service.listMine("newcomer@example.com")).hasSize(1);
    }

    @Test
    void adminCanInviteMembersAndAdminsButNotOwners() {
        service.invite(admin.id(), workspace, "a@example.com", WorkspaceRole.MEMBER);
        service.invite(admin.id(), workspace, "b@example.com", WorkspaceRole.ADMIN);

        assertThatThrownBy(() -> service.invite(admin.id(), workspace, "c@example.com", WorkspaceRole.OWNER))
                .isInstanceOf(ForbiddenException.class);
        assertThat(invitations.stored).hasSize(2);
    }

    @Test
    void aPlainMemberCannotInviteAnyone() {
        assertThatThrownBy(() -> service.invite(member.id(), workspace, "a@example.com", WorkspaceRole.MEMBER))
                .isInstanceOf(ForbiddenException.class);
        assertThat(invitations.stored).isEmpty();
        assertThat(notifier.sent).isEmpty();
    }

    @Test
    void anOutsiderCannotInviteAndGetsNotFound() {
        assertThatThrownBy(() -> service.invite(invitee.id(), workspace, "a@example.com", WorkspaceRole.MEMBER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void existingMembersCannotBeInvitedAgain() {
        assertThatThrownBy(() -> service.invite(owner.id(), workspace, "MIA@example.com", WorkspaceRole.MEMBER))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code()).isEqualTo("invitation.already_member");
    }

    @Test
    void theSameAddressCannotHaveTwoPendingInvitations() {
        service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> service.invite(admin.id(), workspace, "ivy@example.com", WorkspaceRole.ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code()).isEqualTo("invitation.already_pending");
        assertThat(notifier.sent).hasSize(1);
    }

    @Test
    void anInvalidEmailIsRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> service.invite(owner.id(), workspace, "nope", WorkspaceRole.MEMBER))
                .isInstanceOf(ValidationException.class);
        assertThat(invitations.stored).isEmpty();
    }

    @Test
    void onlyAdminsAndOwnersSeeTheInvitationsOfAWorkspace() {
        service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER);

        assertThat(service.listForWorkspace(admin.id(), workspace)).hasSize(1);
        assertThat(service.listForWorkspace(owner.id(), workspace)).hasSize(1);
        assertThatThrownBy(() -> service.listForWorkspace(member.id(), workspace)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.listForWorkspace(invitee.id(), workspace)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokingRemovesTheInvitationFromEveryList() {
        UUID id = service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER).id();

        service.revoke(admin.id(), workspace, id);

        assertThat(service.listForWorkspace(owner.id(), workspace)).isEmpty();
        assertThat(service.listMine("ivy@example.com")).isEmpty();
        assertThatThrownBy(() -> service.accept(invitee.id(), "ivy@example.com", id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void membersCannotRevokeAndInvitationsOfOtherWorkspacesAreNotReachable() {
        UUID id = service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER).id();
        UserView otherOwner = users.add("Oscar", "oscar@example.com");
        UUID otherWorkspace = workspaceService.create(otherOwner.id(), "Other").id();

        assertThatThrownBy(() -> service.revoke(member.id(), workspace, id)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.revoke(otherOwner.id(), otherWorkspace, id)).isInstanceOf(NotFoundException.class);
        assertThat(service.listMine("ivy@example.com")).hasSize(1);
    }

    @Test
    void acceptingJoinsWithTheInvitedRole() {
        UUID id = service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.ADMIN).id();

        WorkspaceView joined = service.accept(invitee.id(), "Ivy@Example.com", id);

        assertThat(joined.id()).isEqualTo(workspace);
        assertThat(joined.role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(members.find(new WorkspaceId(workspace), invitee.id())).get()
                .satisfies(m -> assertThat(m.role()).isEqualTo(Role.ADMIN));
        assertThat(service.listMine("ivy@example.com")).isEmpty();
    }

    @Test
    void anInvitationCannotBeAcceptedTwice() {
        UUID id = service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER).id();
        service.accept(invitee.id(), "ivy@example.com", id);

        assertThatThrownBy(() -> service.accept(invitee.id(), "ivy@example.com", id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void someoneElsesInvitationLooksExactlyLikeAMissingOne() {
        UUID id = service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER).id();
        UserView mallory = users.add("Mallory", "mallory@example.com");

        Throwable stolen = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.accept(mallory.id(), "mallory@example.com", id));
        Throwable missing = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.accept(mallory.id(), "mallory@example.com", UUID.randomUUID()));

        assertThat(stolen).isInstanceOf(NotFoundException.class);
        assertThat(missing).isInstanceOf(NotFoundException.class);
        assertThat(stolen.getMessage()).isEqualTo(missing.getMessage());
        assertThat(members.find(new WorkspaceId(workspace), mallory.id())).isEmpty();
    }

    @Test
    void acceptingWhileAlreadyAMemberKeepsTheExistingRoleAndClosesTheInvitation() {
        UUID id = service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER).id();
        members.save(Member.join(new WorkspaceId(workspace), invitee.id(), Role.ADMIN, Fakes.clock().instant()));

        WorkspaceView joined = service.accept(invitee.id(), "ivy@example.com", id);

        assertThat(joined.role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(members.findByUser(invitee.id())).hasSize(1);
        assertThat(service.listMine("ivy@example.com")).isEmpty();
    }

    @Test
    void listMineOnlyShowsInvitationsForMyEmail() {
        service.invite(owner.id(), workspace, "ivy@example.com", WorkspaceRole.MEMBER);
        service.invite(owner.id(), workspace, "other@example.com", WorkspaceRole.MEMBER);

        assertThat(service.listMine("IVY@example.com")).extracting(InvitationView::email).containsExactly("ivy@example.com");
    }
}
