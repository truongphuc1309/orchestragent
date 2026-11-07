package com.orchestragent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.application.Fakes.InMemoryMembers;
import com.orchestragent.workspace.application.Fakes.InMemoryWorkspaces;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceAccessServiceTest {

    private final InMemoryMembers members = new InMemoryMembers();
    private final InMemoryWorkspaces workspaces = new InMemoryWorkspaces(members);
    private final WorkspaceAccessService access = new WorkspaceAccessService(members);
    private final UUID owner = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID workspace = new WorkspaceService(workspaces, members, Fakes.clock()).create(owner, "Acme").id();

    {
        members.save(Member.join(new WorkspaceId(workspace), admin, Role.ADMIN, Fakes.clock().instant()));
        members.save(Member.join(new WorkspaceId(workspace), member, Role.MEMBER, Fakes.clock().instant()));
    }

    @Test
    void membersPassTheMembershipCheck() {
        assertThatCode(() -> access.requireMember(workspace, member)).doesNotThrowAnyException();
        assertThat(access.isMember(workspace, member)).isTrue();
    }

    @Test
    void outsidersAndUnknownWorkspacesBothLookLikeNotFound() {
        assertThatThrownBy(() -> access.requireMember(workspace, outsider)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> access.requireMember(UUID.randomUUID(), owner)).isInstanceOf(NotFoundException.class);
        assertThat(access.isMember(workspace, outsider)).isFalse();
    }

    @Test
    void requireRoleReturnsTheActualRoleWhenItIsEnough() {
        assertThat(access.requireRole(workspace, owner, WorkspaceRole.ADMIN)).isEqualTo(WorkspaceRole.OWNER);
        assertThat(access.requireRole(workspace, admin, WorkspaceRole.ADMIN)).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(access.requireRole(workspace, member, WorkspaceRole.MEMBER)).isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    void requireRoleRejectsALowerRoleWithForbiddenAndAnOutsiderWithNotFound() {
        assertThatThrownBy(() -> access.requireRole(workspace, member, WorkspaceRole.ADMIN))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> access.requireRole(workspace, admin, WorkspaceRole.OWNER))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> access.requireRole(workspace, outsider, WorkspaceRole.MEMBER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsTheIdsOfAllMembers() {
        assertThat(access.listMemberIds(workspace)).containsExactlyInAnyOrder(owner, admin, member);
        assertThat(access.listMemberIds(UUID.randomUUID())).isEmpty();
    }
}
