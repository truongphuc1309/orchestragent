package com.orchestragent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.application.Fakes.InMemoryMembers;
import com.orchestragent.workspace.application.Fakes.InMemoryWorkspaces;
import com.orchestragent.workspace.application.Views.WorkspaceView;
import com.orchestragent.workspace.contract.WorkspaceRole;
import com.orchestragent.workspace.domain.Member;
import com.orchestragent.workspace.domain.Role;
import com.orchestragent.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceServiceTest {

    private final InMemoryMembers members = new InMemoryMembers();
    private final InMemoryWorkspaces workspaces = new InMemoryWorkspaces(members);
    private final WorkspaceService service = new WorkspaceService(workspaces, members, Fakes.clock());
    private final UUID ada = UUID.randomUUID();
    private final UUID grace = UUID.randomUUID();

    @Test
    void creatorBecomesTheOwner() {
        WorkspaceView created = service.create(ada, "  Acme Team ");

        assertThat(created.name()).isEqualTo("Acme Team");
        assertThat(created.slug()).isEqualTo("acme-team");
        assertThat(created.role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(members.find(new WorkspaceId(created.id()), ada)).get().satisfies(m -> assertThat(m.role()).isEqualTo(Role.OWNER));
    }

    @Test
    void sameNameGetsADifferentSlug() {
        String first = service.create(ada, "Acme").slug();
        String second = service.create(grace, "Acme").slug();
        String third = service.create(grace, "ACME").slug();

        assertThat(first).isEqualTo("acme");
        assertThat(second).isEqualTo("acme-2");
        assertThat(third).isEqualTo("acme-3");
    }

    @Test
    void aCounterSuffixNeverCollidesWithAnExistingSlug() {
        service.create(ada, "Team 2"); // takes "team-2"
        service.create(ada, "Team"); // takes "team"

        // "team" and "team-2" are both taken, so the next free one is "team-3"
        assertThat(service.create(ada, "Team").slug()).isEqualTo("team-3");
    }

    @Test
    void whenAllSequentialSlugsAreTakenARandomSuffixIsUsed() {
        for (int i = 0; i < 51; i++) {
            service.create(ada, "Busy");
        }

        String slug = service.create(ada, "Busy").slug();

        assertThat(slug).startsWith("busy-").hasSize("busy-".length() + 6);
        assertThat(workspaces.stored.values().stream().map(w -> w.slug()).distinct().count()).isEqualTo(52);
    }

    @Test
    void invalidNamesAreRejected() {
        assertThatThrownBy(() -> service.create(ada, "   ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(ada, "x".repeat(61))).isInstanceOf(ValidationException.class);
        assertThat(workspaces.stored).isEmpty();
    }

    @Test
    void listMineOnlyShowsMyWorkspacesWithMyRole() {
        service.create(ada, "Ada's place");
        WorkspaceView shared = service.create(grace, "Graces");
        members.save(Member.join(new WorkspaceId(shared.id()), ada, Role.MEMBER, Fakes.clock().instant()));
        service.create(UUID.randomUUID(), "Somebody else's");

        assertThat(service.listMine(ada))
                .extracting(WorkspaceView::name, WorkspaceView::role)
                .containsExactlyInAnyOrder(tuple("Ada's place", WorkspaceRole.OWNER), tuple("Graces", WorkspaceRole.MEMBER));
        assertThat(service.listMine(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getReturnsTheWorkspaceToMembersOnly() {
        WorkspaceView created = service.create(ada, "Acme");

        assertThat(service.get(ada, created.id()).name()).isEqualTo("Acme");
        assertThatThrownBy(() -> service.get(grace, created.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(ada, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
