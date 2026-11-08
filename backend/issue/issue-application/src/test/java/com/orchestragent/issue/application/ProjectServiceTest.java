package com.orchestragent.issue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.issue.application.Fakes.FakeAccess;
import com.orchestragent.issue.application.Fakes.FakeUsers;
import com.orchestragent.issue.application.Fakes.InMemoryProjects;
import com.orchestragent.issue.application.ProjectService.CreateProject;
import com.orchestragent.issue.application.ProjectService.UpdateProject;
import com.orchestragent.issue.application.Views.ProjectView;
import com.orchestragent.issue.contract.ProjectChanged;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {

    private final InMemoryProjects projects = new InMemoryProjects();
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final List<Object> published = new ArrayList<>();
    private final ProjectService service =
            new ProjectService(projects, access, users, new IssueEvents(published::add, Fakes.clock()), Fakes.clock());

    private final UserView owner = users.add("Olivia");
    private final UserView admin = users.add("Adam");
    private final UserView member = users.add("Mia");
    private final UserView otherMember = users.add("Max");
    private final UserView outsider = users.add("Otto");
    private final UUID workspace = UUID.randomUUID();
    private final UUID otherWorkspace = UUID.randomUUID();

    {
        access.join(workspace, owner.id(), WorkspaceRole.OWNER);
        access.join(workspace, admin.id(), WorkspaceRole.ADMIN);
        access.join(workspace, member.id(), WorkspaceRole.MEMBER);
        access.join(workspace, otherMember.id(), WorkspaceRole.MEMBER);
        access.join(otherWorkspace, outsider.id(), WorkspaceRole.OWNER);
    }

    private ProjectView create(UserView by, String key) {
        return service.create(by.id(), workspace, new CreateProject(key, key + " app", null));
    }

    private List<ProjectChanged> changes() {
        return published.stream().filter(ProjectChanged.class::isInstance).map(ProjectChanged.class::cast).toList();
    }

    // ---- create ------------------------------------------------------------------------------------------

    @Test
    void anyMemberCreatesAProjectWithANormalizedKey() {
        ProjectView created = service.create(member.id(), workspace, new CreateProject(" web ", "  Web app ", " Pages "));

        assertThat(created.key()).isEqualTo("WEB");
        assertThat(created.name()).isEqualTo("Web app");
        assertThat(created.description()).isEqualTo("Pages");
        assertThat(created.createdBy()).isEqualTo(member.id());
        assertThat(created.createdByName()).isEqualTo("Mia");
        assertThat(created.archived()).isFalse();
        assertThat(changes()).singleElement().satisfies(event -> {
            assertThat(event.workspaceId()).isEqualTo(workspace);
            assertThat(event.projectId()).isEqualTo(created.id());
        });
    }

    @Test
    void aKeyIsUniqueInTheWorkspaceIgnoringCaseAndArchivedProjectsKeepTheirs() {
        ProjectView web = create(member, "WEB");
        service.archive(member.id(), workspace, web.id());

        assertThatThrownBy(() -> create(admin, "web"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("project.key_taken");
        assertThat(projects.stored).hasSize(1);
    }

    @Test
    void theSameKeyMayBeUsedInAnotherWorkspace() {
        create(member, "WEB");

        ProjectView elsewhere = service.create(outsider.id(), otherWorkspace, new CreateProject("WEB", "Theirs", null));

        assertThat(elsewhere.key()).isEqualTo("WEB");
    }

    @Test
    void aBadKeyOrNameIsRefusedAndNothingIsStored() {
        assertThatThrownBy(() -> create(member, "1WEB"))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("project.invalid_key");
        assertThatThrownBy(() -> service.create(member.id(), workspace, new CreateProject("WEB", "  ", null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("project.invalid_name");
        assertThat(projects.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void anOutsiderCannotCreateOrSeeProjects() {
        create(member, "WEB");

        assertThatThrownBy(() -> create(outsider, "OUT")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.list(outsider.id(), workspace, true)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByKey(outsider.id(), workspace, "WEB")).isInstanceOf(NotFoundException.class);
    }

    // ---- read --------------------------------------------------------------------------------------------

    @Test
    void listsByKeyAndArchivedOnlyWhenAsked() {
        create(member, "WEB");
        ProjectView api = create(member, "API");
        create(member, "DOC");
        service.archive(member.id(), workspace, api.id());

        assertThat(service.list(member.id(), workspace, false)).extracting(ProjectView::key).containsExactly("DOC", "WEB");
        assertThat(service.list(member.id(), workspace, true)).extracting(ProjectView::key).containsExactly("API", "DOC", "WEB");
    }

    @Test
    void findsAProjectByKeyIgnoringCase() {
        ProjectView web = create(member, "WEB");

        assertThat(service.getByKey(otherMember.id(), workspace, "web").id()).isEqualTo(web.id());
        assertThat(service.get(otherMember.id(), workspace, web.id()).key()).isEqualTo("WEB");
        assertThatThrownBy(() -> service.getByKey(member.id(), workspace, "NOPE")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByKey(member.id(), workspace, "../x")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aProjectOfAnotherWorkspaceIsNotFoundEvenForSomeoneInBothWorkspaces() {
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);
        ProjectView theirs = service.create(outsider.id(), otherWorkspace, new CreateProject("OTH", "Theirs", null));

        assertThatThrownBy(() -> service.get(member.id(), workspace, theirs.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(member.id(), workspace, theirs.id(), new UpdateProject("Mine", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.archive(owner.id(), workspace, theirs.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByKey(member.id(), workspace, "OTH")).isInstanceOf(NotFoundException.class);
    }

    // ---- change ------------------------------------------------------------------------------------------

    @Test
    void theCreatorAdminsAndOwnersChangeAProjectButOtherMembersCannot() {
        ProjectView web = create(member, "WEB");

        service.update(member.id(), workspace, web.id(), new UpdateProject("By creator", null));
        service.update(admin.id(), workspace, web.id(), new UpdateProject("By admin", null));
        service.update(owner.id(), workspace, web.id(), new UpdateProject(null, "By owner"));

        assertThatThrownBy(() -> service.update(otherMember.id(), workspace, web.id(), new UpdateProject("Nope", null)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("project.manage_forbidden");
        assertThatThrownBy(() -> service.archive(otherMember.id(), workspace, web.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.restore(otherMember.id(), workspace, web.id()))
                .isInstanceOf(ForbiddenException.class);

        ProjectView now = service.get(member.id(), workspace, web.id());
        assertThat(now.name()).isEqualTo("By admin");
        assertThat(now.description()).isEqualTo("By owner");
        assertThat(now.archived()).isFalse();
    }

    @Test
    void anUpdateWithOneBadFieldChangesNothing() {
        ProjectView web = create(member, "WEB");

        assertThatThrownBy(() -> service.update(
                        member.id(), workspace, web.id(), new UpdateProject("Renamed", "x".repeat(2_001))))
                .isInstanceOf(ValidationException.class);

        assertThat(service.get(member.id(), workspace, web.id()).name()).isEqualTo("WEB app");
    }

    @Test
    void anUpdateThatChangesNothingPublishesNothing() {
        ProjectView web = create(member, "WEB");
        published.clear();

        service.update(member.id(), workspace, web.id(), new UpdateProject("WEB app", null));

        assertThat(published).isEmpty();
    }

    @Test
    void archivingAndRestoringAreIdempotentAndAnArchivedProjectCannotBeEdited() {
        ProjectView web = create(member, "WEB");
        published.clear();

        assertThat(service.archive(admin.id(), workspace, web.id()).archived()).isTrue();
        assertThat(service.archive(admin.id(), workspace, web.id()).archived()).isTrue();
        assertThat(changes()).hasSize(1);
        assertThatThrownBy(() -> service.update(member.id(), workspace, web.id(), new UpdateProject("Other", null)))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("project.archived");

        assertThat(service.restore(member.id(), workspace, web.id()).archived()).isFalse();
        assertThat(service.restore(member.id(), workspace, web.id()).archived()).isFalse();
        assertThat(changes()).hasSize(2);
    }

    @Test
    void anUnknownCreatorShowsAPlaceholderName() {
        ProjectView web = create(member, "WEB");
        users.byId.remove(member.id());

        assertThat(service.get(admin.id(), workspace, web.id()).createdByName()).isEqualTo("Unknown user");
    }
}
