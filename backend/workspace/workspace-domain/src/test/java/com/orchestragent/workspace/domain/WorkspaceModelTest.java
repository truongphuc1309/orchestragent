package com.orchestragent.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceModelTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void workspaceNameIsTrimmedAndLimited() {
        assertThat(Workspace.create("  Acme  ", "acme", UUID.randomUUID(), NOW).name()).isEqualTo("Acme");
        assertThatThrownBy(() -> Workspace.create("   ", "acme", UUID.randomUUID(), NOW))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Workspace.create(null, "acme", UUID.randomUUID(), NOW))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Workspace.create("x".repeat(61), "acme", UUID.randomUUID(), NOW))
                .isInstanceOf(ValidationException.class);
        assertThat(Workspace.create("x".repeat(60), "x", UUID.randomUUID(), NOW).name()).hasSize(60);
    }

    @Test
    void workspaceNameCannotContainLineBreaksOrOtherControlCharacters() {
        for (String bad : new String[] {"Acme\r\nBcc: evil@example.com", "Acme\nTeam", "Acme\tTeam", "Ac\u0000me"}) {
            assertThatThrownBy(() -> Workspace.create(bad, "acme", UUID.randomUUID(), NOW))
                    .as(bad.replace("\r", "\\r").replace("\n", "\\n"))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void rolesAreOrderedFromOwnerDown() {
        assertThat(Role.OWNER.atLeast(Role.ADMIN)).isTrue();
        assertThat(Role.ADMIN.atLeast(Role.ADMIN)).isTrue();
        assertThat(Role.MEMBER.atLeast(Role.ADMIN)).isFalse();
        assertThat(Role.ADMIN.outranks(Role.ADMIN)).isFalse();
        assertThat(Role.ADMIN.outranks(Role.MEMBER)).isTrue();
    }

    @Test
    void memberRoleCanChange() {
        Member member = Member.join(WorkspaceId.generate(), UUID.randomUUID(), Role.MEMBER, NOW);

        member.changeRole(Role.ADMIN);

        assertThat(member.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void invitationStartsPendingAndCanBeAcceptedOnce() {
        Invitation invitation = newInvitation();
        assertThat(invitation.isPending()).isTrue();

        invitation.accept();

        assertThat(invitation.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThatThrownBy(invitation::accept).isInstanceOf(ConflictException.class);
        assertThatThrownBy(invitation::revoke).isInstanceOf(ConflictException.class);
    }

    @Test
    void revokedInvitationCannotBeAccepted() {
        Invitation invitation = newInvitation();

        invitation.revoke();

        assertThat(invitation.status()).isEqualTo(InvitationStatus.REVOKED);
        assertThatThrownBy(invitation::accept).isInstanceOf(ConflictException.class);
    }

    private static Invitation newInvitation() {
        return Invitation.issue(WorkspaceId.generate(), Email.of("Ada@Example.com"), Role.MEMBER, UUID.randomUUID(), NOW);
    }
}
