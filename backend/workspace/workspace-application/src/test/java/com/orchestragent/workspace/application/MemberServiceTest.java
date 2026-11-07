package com.orchestragent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.application.Fakes.FakeUsers;
import com.orchestragent.workspace.application.Fakes.InMemoryMembers;
import com.orchestragent.workspace.application.Fakes.InMemoryWorkspaces;
import com.orchestragent.workspace.application.Views.MemberView;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberServiceTest {

    private final InMemoryMembers members = new InMemoryMembers();
    private final InMemoryWorkspaces workspaces = new InMemoryWorkspaces(members);
    private final FakeUsers users = new FakeUsers();
    private final WorkspaceService workspaceService = new WorkspaceService(workspaces, members, Fakes.clock());
    private final MemberService service = new MemberService(members, users);

    private final UserView owner = users.add("Olivia", "olivia@example.com");
    private final UserView admin = users.add("Adam", "adam@example.com");
    private final UserView member = users.add("Mia", "mia@example.com");
    private final UserView outsider = users.add("Otto", "otto@example.com");
    private final UUID workspace = workspaceService.create(owner.id(), "Acme").id();

    {
        join(admin, Role.ADMIN);
        join(member, Role.MEMBER);
    }

    private void join(UserView user, Role role) {
        members.save(Member.join(new WorkspaceId(workspace), user.id(), role, Fakes.clock().instant()));
    }

    private Role roleOf(UserView user) {
        return members.find(new WorkspaceId(workspace), user.id()).orElseThrow().role();
    }

    @Test
    void membersSeeEveryoneWithNamesEmailsAndRoles() {
        assertThat(service.list(member.id(), workspace))
                .extracting(MemberView::name, MemberView::email, MemberView::role)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Olivia", "olivia@example.com", WorkspaceRole.OWNER),
                        org.assertj.core.api.Assertions.tuple("Adam", "adam@example.com", WorkspaceRole.ADMIN),
                        org.assertj.core.api.Assertions.tuple("Mia", "mia@example.com", WorkspaceRole.MEMBER));
    }

    @Test
    void outsidersCannotSeeTheMembersAndGetNotFound() {
        assertThatThrownBy(() -> service.list(outsider.id(), workspace)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerCanPromoteAndDemoteAnyone() {
        service.changeRole(owner.id(), workspace, member.id(), WorkspaceRole.OWNER);
        service.changeRole(owner.id(), workspace, admin.id(), WorkspaceRole.MEMBER);

        assertThat(roleOf(member)).isEqualTo(Role.OWNER);
        assertThat(roleOf(admin)).isEqualTo(Role.MEMBER);
    }

    @Test
    void adminCanPromoteAMemberToAdminButNothingElse() {
        service.changeRole(admin.id(), workspace, member.id(), WorkspaceRole.ADMIN);
        assertThat(roleOf(member)).isEqualTo(Role.ADMIN);

        assertThatThrownBy(() -> service.changeRole(admin.id(), workspace, owner.id(), WorkspaceRole.MEMBER))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.changeRole(admin.id(), workspace, member.id(), WorkspaceRole.MEMBER))
                .as("demoting another admin").isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCannotCreateAnOwner() {
        UserView other = users.add("Nina", "nina@example.com");
        join(other, Role.MEMBER);

        assertThatThrownBy(() -> service.changeRole(admin.id(), workspace, other.id(), WorkspaceRole.OWNER))
                .isInstanceOf(ForbiddenException.class);
        assertThat(roleOf(other)).isEqualTo(Role.MEMBER);
    }

    @Test
    void aPlainMemberCannotChangeAnyRoleNotEvenTheirOwn() {
        assertThatThrownBy(() -> service.changeRole(member.id(), workspace, member.id(), WorkspaceRole.ADMIN))
                .isInstanceOf(ForbiddenException.class);
        assertThat(roleOf(member)).isEqualTo(Role.MEMBER);
    }

    @Test
    void outsidersGetNotFoundWhenChangingRoles() {
        assertThatThrownBy(() -> service.changeRole(outsider.id(), workspace, member.id(), WorkspaceRole.ADMIN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changingTheRoleOfSomeoneWhoIsNotAMemberIsNotFound() {
        assertThatThrownBy(() -> service.changeRole(owner.id(), workspace, outsider.id(), WorkspaceRole.ADMIN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void theLastOwnerCannotBeDemoted() {
        assertThatThrownBy(() -> service.changeRole(owner.id(), workspace, owner.id(), WorkspaceRole.ADMIN))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).code()).isEqualTo("workspace.last_owner");
        assertThat(roleOf(owner)).isEqualTo(Role.OWNER);
    }

    @Test
    void anOwnerCanStepDownOnceAnotherOwnerExists() {
        service.changeRole(owner.id(), workspace, member.id(), WorkspaceRole.OWNER);

        service.changeRole(owner.id(), workspace, owner.id(), WorkspaceRole.ADMIN);

        assertThat(roleOf(owner)).isEqualTo(Role.ADMIN);
    }

    @Test
    void ownerCanRemoveAnyoneButTheLastOwner() {
        service.remove(owner.id(), workspace, admin.id());
        service.remove(owner.id(), workspace, member.id());

        assertThat(members.findByWorkspace(new WorkspaceId(workspace))).extracting(Member::userId).containsExactly(owner.id());
        assertThatThrownBy(() -> service.remove(owner.id(), workspace, owner.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCanRemoveMembersButNotAdminsOrOwners() {
        UserView otherAdmin = users.add("Ann", "ann@example.com");
        join(otherAdmin, Role.ADMIN);

        service.remove(admin.id(), workspace, member.id());
        assertThatThrownBy(() -> service.remove(admin.id(), workspace, otherAdmin.id())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.remove(admin.id(), workspace, owner.id())).isInstanceOf(ForbiddenException.class);
        assertThat(members.find(new WorkspaceId(workspace), member.id())).isEmpty();
    }

    @Test
    void memberCannotRemoveOthersButCanLeave() {
        assertThatThrownBy(() -> service.remove(member.id(), workspace, admin.id())).isInstanceOf(ForbiddenException.class);

        service.remove(member.id(), workspace, member.id());

        assertThat(members.find(new WorkspaceId(workspace), member.id())).isEmpty();
    }

    @Test
    void anOwnerLeavingNeedsAnotherOwner() {
        assertThatThrownBy(() -> service.remove(owner.id(), workspace, owner.id())).isInstanceOf(ConflictException.class);

        service.changeRole(owner.id(), workspace, admin.id(), WorkspaceRole.OWNER);
        service.remove(owner.id(), workspace, owner.id());

        assertThat(members.find(new WorkspaceId(workspace), owner.id())).isEmpty();
    }

    @Test
    void outsidersGetNotFoundWhenRemoving() {
        assertThatThrownBy(() -> service.remove(outsider.id(), workspace, member.id())).isInstanceOf(NotFoundException.class);
        assertThat(members.find(new WorkspaceId(workspace), member.id())).isPresent();
    }
}
