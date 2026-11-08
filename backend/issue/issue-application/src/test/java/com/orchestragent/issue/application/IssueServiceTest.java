package com.orchestragent.issue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.identity.contract.UserView;
import com.orchestragent.issue.application.Fakes.FakeAccess;
import com.orchestragent.issue.application.Fakes.FakeAgents;
import com.orchestragent.issue.application.Fakes.FakeUsers;
import com.orchestragent.issue.application.Fakes.InMemoryIssues;
import com.orchestragent.issue.application.Fakes.InMemoryNumbers;
import com.orchestragent.issue.application.Fakes.InMemoryProjects;
import com.orchestragent.issue.application.IssueService.CreateIssue;
import com.orchestragent.issue.application.IssueService.UpdateIssue;
import com.orchestragent.issue.application.Views.IssuePageView;
import com.orchestragent.issue.application.Views.IssueView;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueCreated;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueUpdated;
import com.orchestragent.issue.domain.Actor;
import com.orchestragent.issue.domain.ActorType;
import com.orchestragent.issue.domain.Issue;
import com.orchestragent.issue.domain.IssueFilter;
import com.orchestragent.issue.domain.Positions;
import com.orchestragent.issue.domain.Priority;
import com.orchestragent.issue.domain.Project;
import com.orchestragent.issue.domain.Status;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssueServiceTest {

    private final InMemoryIssues issues = new InMemoryIssues();

    private final InMemoryProjects projects = new InMemoryProjects();
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final FakeAgents agents = new FakeAgents();
    private final List<Object> published = new ArrayList<>();
    private final IssueEvents events = new IssueEvents(published::add, Fakes.clock());
    private final IssueService service = new IssueService(
            issues, projects, new InMemoryNumbers(projects), access, agents, new ActorNames(users, agents), events, Fakes.clock());

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

    private CreateIssue issue(String title) {
        return issueIn(workspace, title);
    }

    private CreateIssue issueIn(UUID workspaceId, String title) {
        return new CreateIssue(projects.defaultIn(workspaceId), title, null, null, null, null);
    }

    private CreateIssue issue(String title, Status status) {
        return new CreateIssue(projects.defaultIn(workspace), title, null, status, null, null);
    }

    private IssueView create(String title, Status status) {
        return service.create(member.id(), workspace, issue(title, status));
    }

    /** IDs of a column from top to bottom, as the board shows them. */
    private List<String> column(Status status) {
        return issues.findByStatus(workspace, status).stream().map(Issue::title).toList();
    }

    private <T> List<T> eventsOf(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }

    // ---- create ------------------------------------------------------------------------------------------

    @Test
    void createsAnIssueWithDefaultsAndTheCreatorFilledIn() {
        IssueView created = service.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "  Fix login  ", "Steps", null, null, null));

        assertThat(created.number()).isEqualTo(1);
        assertThat(created.title()).isEqualTo("Fix login");
        assertThat(created.description()).isEqualTo("Steps");
        assertThat(created.status()).isEqualTo(Status.TODO);
        assertThat(created.priority()).isEqualTo(Priority.NONE);
        assertThat(created.assignee()).isNull();
        assertThat(created.creator().type()).isEqualTo(ActorType.MEMBER);
        assertThat(created.creator().id()).isEqualTo(member.id());
        assertThat(created.creator().name()).isEqualTo("Mia");
        assertThat(created.workspaceId()).isEqualTo(workspace);
    }

    @Test
    void numbersGoUpPerProjectAndTheKeyFollowsTheProject() {
        // Numbers used to count per workspace; since projects, every project counts on its own (step 13).
        UUID api = projects.add(workspace, "API").id().value();
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);

        assertThat(create("a", null).key()).isEqualTo("WEB-1");
        assertThat(create("b", null).key()).isEqualTo("WEB-2");
        IssueView inApi = service.create(member.id(), workspace, new CreateIssue(api, "c", null, null, null, null));
        assertThat(service.create(member.id(), otherWorkspace, issueIn(otherWorkspace, "d")).key()).isEqualTo("WEB-1");
        assertThat(create("e", null).key()).isEqualTo("WEB-3");

        assertThat(inApi.key()).isEqualTo("API-1");
        assertThat(inApi.number()).isEqualTo(1);
        assertThat(inApi.projectId()).isEqualTo(api);
    }

    @Test
    void anIssueNeedsAnActiveProjectOfTheWorkspace() {
        Project old = projects.add(workspace, "OLD");
        old.archive(Fakes.clock().instant());
        UUID archived = old.id().value();
        UUID foreign = projects.defaultIn(otherWorkspace);

        assertThatThrownBy(() -> service.create(member.id(), workspace, new CreateIssue(null, "x", null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("issue.project_required");
        assertThatThrownBy(() -> service.create(member.id(), workspace, new CreateIssue(foreign, "x", null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("issue.invalid_project");
        assertThatThrownBy(() -> service.create(member.id(), workspace, new CreateIssue(archived, "x", null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("project.archived");
        assertThat(issues.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void aRefusedIssueTakesNoNumber() {
        assertThatThrownBy(() -> create("  ", null)).isInstanceOf(ValidationException.class);

        assertThat(create("Fix", null).key()).isEqualTo("WEB-1");
    }

    @Test
    void newIssuesGoToTheBottomOfTheirColumn() {
        IssueView first = create("first", Status.TODO);
        IssueView second = create("second", Status.TODO);
        IssueView other = create("other column", Status.BACKLOG);

        assertThat(first.position()).isEqualTo(Positions.STEP);
        assertThat(second.position()).isGreaterThan(first.position());
        assertThat(other.position()).isEqualTo(Positions.STEP);
        assertThat(column(Status.TODO)).containsExactly("first", "second");
    }

    @Test
    void publishesIssueCreated() {
        IssueView created = create("Fix", null);

        assertThat(eventsOf(IssueCreated.class)).singleElement().satisfies(event -> {
            assertThat(event.workspaceId()).isEqualTo(workspace);
            assertThat(event.issueId()).isEqualTo(created.id());
            assertThat(event.projectId()).isEqualTo(created.projectId());
            assertThat(event.key()).isEqualTo(created.key());
            assertThat(event.creatorId()).isEqualTo(member.id());
            assertThat(event.eventId()).isNotNull();
        });
        assertThat(eventsOf(IssueAssigneeChanged.class)).as("nobody was assigned").isEmpty();
    }

    @Test
    void creatingWithAnAssigneeAlsoPublishesAssigneeChanged() {
        IssueView created = service.create(
                member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "Fix", null, null, null, Actor.member(otherMember.id())));

        assertThat(created.assignee().name()).isEqualTo("Max");
        assertThat(eventsOf(IssueAssigneeChanged.class)).singleElement().satisfies(event -> {
            assertThat(event.issueId()).isEqualTo(created.id());
            assertThat(event.previousAssigneeType()).isNull();
            assertThat(event.previousAssigneeId()).isNull();
            assertThat(event.newAssigneeType()).isEqualTo(com.orchestragent.issue.contract.ActorType.MEMBER);
            assertThat(event.newAssigneeId()).isEqualTo(otherMember.id());
            assertThat(event.actorId()).isEqualTo(member.id());
        });
    }

    @Test
    void anOutsiderCannotCreateAnIssueAndNothingIsStored() {
        assertThatThrownBy(() -> service.create(outsider.id(), workspace, issue("Sneaky")))
                .isInstanceOf(NotFoundException.class);

        assertThat(issues.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsAnInvalidTitleWithoutStoringOrPublishing() {
        assertThatThrownBy(() -> service.create(member.id(), workspace, issue("  ")))
                .isInstanceOf(ValidationException.class);

        assertThat(issues.stored).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsAnAssigneeWhoIsNotInTheWorkspace() {
        assertThatThrownBy(() -> service.create(
                        member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "Fix", null, null, null, Actor.member(outsider.id()))))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("issue.invalid_assignee");
        assertThat(issues.stored).isEmpty();
    }

    @Test
    void rejectsAnAgentThatDoesNotExist() {
        assertThatThrownBy(() -> service.create(
                        member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "Fix", null, null, null, Actor.agent(UUID.randomUUID()))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsAnExistingAgentAsAssignee() {
        UUID agent = agents.add("Builder");

        IssueView created =
                service.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "Fix", null, null, null, Actor.agent(agent)));

        assertThat(created.assignee().type()).isEqualTo(ActorType.AGENT);
        assertThat(created.assignee().name()).isEqualTo("Builder");
    }

    // ---- read --------------------------------------------------------------------------------------------

    @Test
    void getsAnIssueByIdAndByKey() {
        IssueView created = create("Fix", null);
        UUID api = projects.add(workspace, "API").id().value();
        IssueView inApi = service.create(member.id(), workspace, new CreateIssue(api, "Other", null, null, null, null));

        assertThat(service.get(admin.id(), workspace, created.id()).title()).isEqualTo("Fix");
        assertThat(service.getByKey(admin.id(), workspace, "WEB-1").id()).isEqualTo(created.id());
        assertThat(service.getByKey(admin.id(), workspace, "API-1").id()).isEqualTo(inApi.id());
    }

    @Test
    void aKeyThatIsMalformedOrOfAnotherWorkspaceIsNotFound() {
        create("Fix", null);
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);
        UUID foreign = projects.add(otherWorkspace, "OTH").id().value();
        service.create(member.id(), otherWorkspace, new CreateIssue(foreign, "Elsewhere", null, null, null, null));

        for (String key : List.of("web-1", "WEB-2", "WEB", "WEB-0", "NOPE-1", "OTH-1", "")) {
            assertThatThrownBy(() -> service.getByKey(member.id(), workspace, key))
                    .as(key)
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void anOutsiderGetsNotFoundForReads() {
        IssueView created = create("Fix", null);

        assertThatThrownBy(() -> service.get(outsider.id(), workspace, created.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByKey(outsider.id(), workspace, "WEB-1")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.list(outsider.id(), workspace, IssueFilter.none(), 10))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anIssueOfAnotherWorkspaceIsNotFoundEvenForSomeoneInBothWorkspaces() {
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);
        IssueView inOther = service.create(member.id(), otherWorkspace, issueIn(otherWorkspace, "Elsewhere"));

        assertThatThrownBy(() -> service.get(member.id(), workspace, inOther.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(member.id(), workspace, inOther.id(), new UpdateIssue("Hijack", null, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.assign(member.id(), workspace, inOther.id(), null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.move(member.id(), workspace, inOther.id(), Status.DONE, null, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(member.id(), workspace, inOther.id())).isInstanceOf(NotFoundException.class);
        assertThat(service.get(member.id(), otherWorkspace, inOther.id()).title()).isEqualTo("Elsewhere");
    }

    @Test
    void anUnknownIssueIsNotFound() {
        assertThatThrownBy(() -> service.get(member.id(), workspace, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getByKey(member.id(), workspace, "WEB-99")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsNewestFirstAndOnlyFromTheWorkspace() {
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);
        create("one", null);
        create("two", null);
        service.create(member.id(), otherWorkspace, issueIn(otherWorkspace, "not here"));

        IssuePageView page = service.list(admin.id(), workspace, IssueFilter.none(), 100);

        assertThat(page.items()).extracting(IssueView::title).containsExactly("two", "one");
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void listFiltersByStatusPriorityAndAssignee() {
        service.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "a", null, Status.TODO, Priority.HIGH, Actor.member(admin.id())));
        service.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "b", null, Status.DONE, Priority.HIGH, null));
        service.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "c", null, Status.TODO, Priority.LOW, Actor.member(member.id())));
        service.create(member.id(), workspace, new CreateIssue(projects.defaultIn(workspace), "d", null, Status.TODO, Priority.HIGH, null));

        assertThat(titles(new IssueFilter(Set.of(), Set.of(Status.TODO), Set.of(), Set.of(), false))).containsExactly("d", "c", "a");
        assertThat(titles(new IssueFilter(Set.of(), Set.of(), Set.of(Priority.HIGH), Set.of(), false))).containsExactly("d", "b", "a");
        assertThat(titles(new IssueFilter(Set.of(), Set.of(), Set.of(), Set.of(admin.id()), false))).containsExactly("a");
        assertThat(titles(new IssueFilter(Set.of(), Set.of(), Set.of(), Set.of(), true))).containsExactly("d", "b");
        assertThat(titles(new IssueFilter(Set.of(), Set.of(), Set.of(), Set.of(member.id()), true))).containsExactly("d", "c", "b");
        assertThat(titles(new IssueFilter(Set.of(), Set.of(Status.TODO), Set.of(Priority.HIGH), Set.of(), true))).containsExactly("d");
    }

    @Test
    void listFiltersByProject() {
        UUID web = projects.defaultIn(workspace);
        UUID api = projects.add(workspace, "API").id().value();
        UUID docs = projects.add(workspace, "DOC").id().value();
        service.create(member.id(), workspace, new CreateIssue(web, "w", null, null, null, null));
        service.create(member.id(), workspace, new CreateIssue(api, "a", null, null, null, null));
        service.create(member.id(), workspace, new CreateIssue(docs, "d", null, null, null, null));

        assertThat(titles(IssueFilter.none())).containsExactly("d", "a", "w");
        assertThat(titles(new IssueFilter(Set.of(api), Set.of(), Set.of(), Set.of(), false))).containsExactly("a");
        assertThat(titles(new IssueFilter(Set.of(web, docs), Set.of(), Set.of(), Set.of(), false))).containsExactly("d", "w");
    }

    private List<String> titles(IssueFilter filter) {
        return service.list(member.id(), workspace, filter, 100).items().stream().map(IssueView::title).toList();
    }

    @Test
    void theLimitCutsTheListButTheTotalStaysHonest() {
        for (int i = 1; i <= 5; i++) {
            create("issue " + i, null);
        }

        IssuePageView page = service.list(member.id(), workspace, IssueFilter.none(), 2);

        assertThat(page.items()).extracting(IssueView::title).containsExactly("issue 5", "issue 4");
        assertThat(page.total()).isEqualTo(5);
    }

    @Test
    void anAbsurdLimitIsClamped() {
        create("one", null);

        assertThat(service.list(member.id(), workspace, IssueFilter.none(), 0).items()).hasSize(1);
        assertThat(service.list(member.id(), workspace, IssueFilter.none(), Integer.MAX_VALUE).items()).hasSize(1);
    }

    @Test
    void anUnknownAssigneeShowsAPlaceholderNameInsteadOfFailing() {
        UUID gone = UUID.randomUUID();
        IssueView created = create("Fix", null);
        // Assign directly in the store: the user has since left the workspace and the identity directory.
        Issue stored = issues.stored.values().iterator().next();
        stored.assign(Actor.member(gone), Fakes.clock().instant());

        assertThat(service.get(member.id(), workspace, created.id()).assignee().name()).isEqualTo("Unknown user");
    }

    // ---- update ------------------------------------------------------------------------------------------

    @Test
    void updatesTheGivenFieldsOnly() {
        IssueView created = create("Old title", null);
        published.clear();

        IssueView updated = service.update(
                otherMember.id(), workspace, created.id(), new UpdateIssue("New title", null, null, Priority.URGENT));

        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.priority()).isEqualTo(Priority.URGENT);
        assertThat(updated.status()).isEqualTo(Status.TODO);
        assertThat(eventsOf(IssueUpdated.class)).singleElement().satisfies(event -> {
            assertThat(event.changedFields()).containsExactlyInAnyOrder("title", "priority");
            assertThat(event.status()).isEqualTo("todo");
            assertThat(event.actorId()).isEqualTo(otherMember.id());
        });
    }

    @Test
    void anUpdateThatChangesNothingPublishesNothing() {
        IssueView created = create("Same", null);
        published.clear();

        service.update(member.id(), workspace, created.id(), new UpdateIssue("Same", "", Status.TODO, Priority.NONE));

        assertThat(published).isEmpty();
    }

    @Test
    void anInvalidUpdateChangesNothing() {
        IssueView created = create("Keep me", null);
        published.clear();

        assertThatThrownBy(() -> service.update(member.id(), workspace, created.id(), new UpdateIssue(" ", null, null, null)))
                .isInstanceOf(ValidationException.class);

        assertThat(service.get(member.id(), workspace, created.id()).title()).isEqualTo("Keep me");
        assertThat(published).isEmpty();
    }

    @Test
    void aNewStatusPutsTheIssueAtTheBottomOfTheNewColumn() {
        IssueView moved = create("moved", Status.TODO);
        create("waiting", Status.IN_PROGRESS);
        published.clear();

        service.update(member.id(), workspace, moved.id(), new UpdateIssue(null, null, Status.IN_PROGRESS, null));

        assertThat(column(Status.IN_PROGRESS)).containsExactly("waiting", "moved");
        assertThat(column(Status.TODO)).isEmpty();
        assertThat(eventsOf(IssueUpdated.class)).singleElement().satisfies(event -> {
            assertThat(event.changedFields()).contains("status");
            assertThat(event.status()).isEqualTo("in_progress");
        });
    }

    @Test
    void anOutsiderCannotUpdate() {
        IssueView created = create("Fix", null);

        assertThatThrownBy(() -> service.update(outsider.id(), workspace, created.id(), new UpdateIssue("X", null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- assign ------------------------------------------------------------------------------------------

    @Test
    void assignsAndUnassigns() {
        IssueView created = create("Fix", null);
        published.clear();

        IssueView assigned = service.assign(member.id(), workspace, created.id(), Actor.member(admin.id()));
        assertThat(assigned.assignee().name()).isEqualTo("Adam");

        IssueView unassigned = service.assign(member.id(), workspace, created.id(), null);
        assertThat(unassigned.assignee()).isNull();

        List<IssueAssigneeChanged> changes = eventsOf(IssueAssigneeChanged.class);
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).newAssigneeId()).isEqualTo(admin.id());
        assertThat(changes.get(1).previousAssigneeId()).isEqualTo(admin.id());
        assertThat(changes.get(1).newAssigneeId()).isNull();
        assertThat(changes.get(1).newAssigneeType()).isNull();
    }

    @Test
    void assigningTheSamePersonAgainPublishesNothing() {
        IssueView created = create("Fix", null);
        service.assign(member.id(), workspace, created.id(), Actor.member(admin.id()));
        published.clear();

        service.assign(member.id(), workspace, created.id(), Actor.member(admin.id()));

        assertThat(published).isEmpty();
    }

    @Test
    void cannotAssignToSomeoneOutsideTheWorkspace() {
        IssueView created = create("Fix", null);

        assertThatThrownBy(() -> service.assign(member.id(), workspace, created.id(), Actor.member(outsider.id())))
                .isInstanceOf(ValidationException.class);
        assertThat(service.get(member.id(), workspace, created.id()).assignee()).isNull();
    }

    // ---- move --------------------------------------------------------------------------------------------

    @Test
    void movesBetweenTwoNeighboursInTheSameColumn() {
        IssueView a = create("a", Status.TODO);
        IssueView b = create("b", Status.TODO);
        IssueView c = create("c", Status.TODO);

        service.move(member.id(), workspace, c.id(), Status.TODO, a.id(), b.id());

        assertThat(column(Status.TODO)).containsExactly("a", "c", "b");
    }

    @Test
    void movesToTheTopAndToTheBottom() {
        IssueView a = create("a", Status.TODO);
        IssueView b = create("b", Status.TODO);
        IssueView c = create("c", Status.TODO);

        service.move(member.id(), workspace, c.id(), Status.TODO, null, a.id());
        assertThat(column(Status.TODO)).containsExactly("c", "a", "b");

        service.move(member.id(), workspace, c.id(), Status.TODO, b.id(), null);
        assertThat(column(Status.TODO)).containsExactly("a", "b", "c");
    }

    @Test
    void movesToAnotherColumnBetweenItsIssues() {
        IssueView moving = create("moving", Status.TODO);
        IssueView x = create("x", Status.IN_REVIEW);
        IssueView y = create("y", Status.IN_REVIEW);
        published.clear();

        IssueView moved = service.move(member.id(), workspace, moving.id(), Status.IN_REVIEW, x.id(), y.id());

        assertThat(moved.status()).isEqualTo(Status.IN_REVIEW);
        assertThat(column(Status.IN_REVIEW)).containsExactly("x", "moving", "y");
        assertThat(column(Status.TODO)).isEmpty();
        assertThat(eventsOf(IssueUpdated.class)).singleElement().satisfies(event -> {
            assertThat(event.changedFields()).containsExactlyInAnyOrder("status", "position");
            assertThat(event.status()).isEqualTo("in_review");
        });
    }

    @Test
    void droppingInAnEmptyColumnWorks() {
        IssueView only = create("only", Status.TODO);

        IssueView moved = service.move(member.id(), workspace, only.id(), Status.DONE, null, null);

        assertThat(moved.status()).isEqualTo(Status.DONE);
        assertThat(moved.position()).isEqualTo(Positions.STEP);
    }

    @Test
    void withoutNeighboursTheIssueIsAppendedToTheColumn() {
        create("first", Status.DONE);
        IssueView moving = create("moving", Status.TODO);

        service.move(member.id(), workspace, moving.id(), Status.DONE, null, null);

        assertThat(column(Status.DONE)).containsExactly("first", "moving");
    }

    @Test
    void movingToWhereItAlreadyIsPublishesNothing() {
        IssueView a = create("a", Status.TODO);
        IssueView b = create("b", Status.TODO);
        published.clear();

        // "b" is already directly below "a" and last, one step apart: dropping it there again is no change.
        service.move(member.id(), workspace, b.id(), Status.TODO, a.id(), null);

        assertThat(column(Status.TODO)).containsExactly("a", "b");
        assertThat(published).isEmpty();
    }

    @Test
    void keepsTheOrderAfterManyDropsIntoTheSameGap() {
        IssueView top = create("top", Status.TODO);
        IssueView bottom = create("bottom", Status.TODO);
        List<IssueView> dropped = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            dropped.add(create("d" + i, Status.BACKLOG));
        }

        // Each drop goes directly below "top", so the gap between "top" and the previous drop halves every time.
        for (IssueView issue : dropped) {
            service.move(member.id(), workspace, issue.id(), Status.TODO, top.id(), nextBelow(top));
        }

        List<String> expected = new ArrayList<>(List.of("top"));
        for (int i = dropped.size() - 1; i >= 0; i--) {
            expected.add("d" + i);
        }
        expected.add("bottom");
        assertThat(column(Status.TODO)).containsExactlyElementsOf(expected);
        assertThat(service.get(member.id(), workspace, bottom.id()).status()).isEqualTo(Status.TODO);
    }

    /** The ID of the issue that is currently directly below {@code above}, or null when it is last. */
    private UUID nextBelow(IssueView above) {
        List<Issue> column = issues.findByStatus(workspace, Status.TODO);
        for (int i = 0; i < column.size() - 1; i++) {
            if (column.get(i).id().value().equals(above.id())) {
                return column.get(i + 1).id().value();
            }
        }
        return null;
    }

    @Test
    void renumbersTheColumnWhenTwoNeighboursAreTooClose() {
        IssueView a = create("a", Status.TODO);
        IssueView b = create("b", Status.TODO);
        IssueView c = create("c", Status.BACKLOG);
        // Squeeze "b" against "a" until no position fits between them.
        issues.stored.get(new com.orchestragent.issue.domain.IssueId(b.id())).renumber(
                issues.stored.get(new com.orchestragent.issue.domain.IssueId(a.id())).position() + 1e-9);

        service.move(member.id(), workspace, c.id(), Status.TODO, a.id(), b.id());

        assertThat(column(Status.TODO)).containsExactly("a", "c", "b");
        List<Double> positions =
                issues.findByStatus(workspace, Status.TODO).stream().map(Issue::position).toList();
        assertThat(positions).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void aStaleMoveIsRefusedWithAConflict() {
        IssueView a = create("a", Status.TODO);
        IssueView b = create("b", Status.TODO);
        IssueView elsewhere = create("elsewhere", Status.DONE);
        IssueView moving = create("moving", Status.BACKLOG);

        // A neighbour that has moved to another column since the client loaded the board.
        assertThatThrownBy(() -> service.move(member.id(), workspace, moving.id(), Status.TODO, elsewhere.id(), null))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("issue.stale_move");
        // A neighbour that was deleted.
        assertThatThrownBy(() -> service.move(member.id(), workspace, moving.id(), Status.TODO, UUID.randomUUID(), null))
                .isInstanceOf(ConflictException.class);
        // Neighbours in the wrong order (the board was reordered by someone else).
        assertThatThrownBy(() -> service.move(member.id(), workspace, moving.id(), Status.TODO, b.id(), a.id()))
                .isInstanceOf(ConflictException.class);
        assertThat(column(Status.BACKLOG)).containsExactly("moving");
    }

    @Test
    void anIssueCannotBePlacedNextToItself() {
        IssueView a = create("a", Status.TODO);

        assertThatThrownBy(() -> service.move(member.id(), workspace, a.id(), Status.TODO, a.id(), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aNeighbourFromAnotherWorkspaceIsRefused() {
        access.join(otherWorkspace, member.id(), WorkspaceRole.MEMBER);
        IssueView foreign = service.create(member.id(), otherWorkspace, new CreateIssue(projects.defaultIn(otherWorkspace), "foreign", null, Status.TODO, null, null));
        IssueView moving = create("moving", Status.TODO);

        assertThatThrownBy(() -> service.move(member.id(), workspace, moving.id(), Status.TODO, foreign.id(), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anOutsiderCannotMove() {
        IssueView a = create("a", Status.TODO);

        assertThatThrownBy(() -> service.move(outsider.id(), workspace, a.id(), Status.DONE, null, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(column(Status.TODO)).containsExactly("a");
    }

    // ---- delete ------------------------------------------------------------------------------------------

    @Test
    void theCreatorCanDeleteTheirOwnIssue() {
        IssueView created = create("Mine", null);
        published.clear();

        service.delete(member.id(), workspace, created.id());

        assertThat(issues.stored).isEmpty();
        assertThat(eventsOf(IssueDeleted.class)).singleElement().satisfies(event -> {
            assertThat(event.issueId()).isEqualTo(created.id());
            assertThat(event.key()).isEqualTo(created.key());
        });
    }

    @Test
    void adminsAndOwnersCanDeleteAnyIssue() {
        IssueView first = create("first", null);
        IssueView second = create("second", null);

        service.delete(admin.id(), workspace, first.id());
        service.delete(owner.id(), workspace, second.id());

        assertThat(issues.stored).isEmpty();
    }

    @Test
    void anotherPlainMemberCannotDelete() {
        IssueView created = create("Not yours", null);
        published.clear();

        assertThatThrownBy(() -> service.delete(otherMember.id(), workspace, created.id()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(issues.stored).hasSize(1);
        assertThat(published).isEmpty();
    }

    @Test
    void anOutsiderCannotDelete() {
        IssueView created = create("Fix", null);

        assertThatThrownBy(() -> service.delete(outsider.id(), workspace, created.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(issues.stored).hasSize(1);
    }
}
