package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.agent.application.AgentService.AgentDetails;
import com.orchestragent.agent.application.Fakes.FakeAccess;
import com.orchestragent.agent.application.Fakes.FakeUsers;
import com.orchestragent.agent.application.Fakes.InMemoryAgents;
import com.orchestragent.agent.application.Fakes.InMemoryRuntimes;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.RuntimeService.ProviderInfo;
import com.orchestragent.agent.application.TaskFakes.RecordingTaskControl;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.application.Views.AgentView;
import com.orchestragent.agent.contract.AgentDirectory;
import com.orchestragent.agent.contract.AgentSummary;
import com.orchestragent.identity.contract.UserView;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    private final InMemoryAgents agents = new InMemoryAgents();
    private final InMemoryRuntimes runtimes = new InMemoryRuntimes();
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final MutableClock clock = new MutableClock();
    private final RecordingTaskControl taskControl = new RecordingTaskControl();
    private final AgentService service = new AgentService(agents, runtimes, access, users, taskControl, clock);
    private final RuntimeService runtimeService = new RuntimeService(runtimes, access, users, clock);
    private final AgentDirectory directory = new AgentDirectoryService(agents);

    private final UserView owner = users.add("Olivia");
    private final UserView admin = users.add("Adam");
    private final UserView mia = users.add("Mia");
    private final UserView max = users.add("Max");
    private final UserView outsider = users.add("Otto");
    private final UUID workspace = UUID.randomUUID();
    private final UUID otherWorkspace = UUID.randomUUID();
    private final UUID miasRuntime;
    private final UUID maxsRuntime;

    {
        access.join(workspace, owner.id(), WorkspaceRole.OWNER);
        access.join(workspace, admin.id(), WorkspaceRole.ADMIN);
        access.join(workspace, mia.id(), WorkspaceRole.MEMBER);
        access.join(workspace, max.id(), WorkspaceRole.MEMBER);
        access.join(otherWorkspace, outsider.id(), WorkspaceRole.OWNER);
        access.join(otherWorkspace, mia.id(), WorkspaceRole.MEMBER);
        miasRuntime = runtimeService
                .register(mia.id(), workspace, "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();
        maxsRuntime = runtimeService
                .register(max.id(), workspace, "max-pc", "Max's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();
    }

    private AgentDetails details(String name) {
        return new AgentDetails(name, "Do the work.", miasRuntime, null);
    }

    private AgentView createByMia(String name) {
        return service.create(mia.id(), workspace, details(name));
    }

    // ---- create ------------------------------------------------------------------------------------------

    @Test
    void createsAnAgentOnTheCreatorsOwnRuntime() {
        AgentView created = service.create(
                mia.id(), workspace, new AgentDetails("  Builder  ", "# Rules\n- be kind", miasRuntime, " sonnet "));

        assertThat(created.name()).isEqualTo("Builder");
        assertThat(created.instructions()).isEqualTo("# Rules\n- be kind");
        assertThat(created.model()).isEqualTo("sonnet");
        assertThat(created.archived()).isFalse();
        assertThat(created.createdBy()).isEqualTo(mia.id());
        assertThat(created.createdByName()).isEqualTo("Mia");
        assertThat(created.runtime().id()).isEqualTo(miasRuntime);
        assertThat(created.runtime().deviceName()).isEqualTo("Mia's PC");
        assertThat(created.runtime().provider()).isEqualTo("claude");
        assertThat(agents.stored).hasSize(1);
    }

    @Test
    void theModelIsOptional() {
        assertThat(service.create(mia.id(), workspace, new AgentDetails("A", "", miasRuntime, "")).model()).isNull();
    }

    @Test
    void cannotUseSomeoneElsesRuntime() {
        assertThatThrownBy(() -> service.create(mia.id(), workspace, new AgentDetails("A", "", maxsRuntime, null)))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("agent.runtime_not_yours");

        assertThat(agents.stored).isEmpty();
    }

    @Test
    void cannotUseARuntimeThatDoesNotExistOrBelongsToAnotherWorkspace() {
        UUID foreign = runtimeService
                .register(mia.id(), otherWorkspace, "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();

        assertThatThrownBy(() -> service.create(mia.id(), workspace, new AgentDetails("A", "", UUID.randomUUID(), null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("agent.invalid_runtime");
        assertThatThrownBy(() -> service.create(mia.id(), workspace, new AgentDetails("A", "", foreign, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(mia.id(), workspace, new AgentDetails("A", "", null, null)))
                .isInstanceOf(ValidationException.class);
        assertThat(agents.stored).isEmpty();
    }

    @Test
    void rejectsAnInvalidNameWithoutStoringAnything() {
        assertThatThrownBy(() -> createByMia("   ")).isInstanceOf(ValidationException.class);

        assertThat(agents.stored).isEmpty();
    }

    @Test
    void activeAgentsOfAWorkspaceHaveDistinctNamesIgnoringCase() {
        createByMia("Builder");

        assertThatThrownBy(() -> createByMia("builder"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("agent.name_taken");
        assertThat(agents.stored).hasSize(1);
    }

    @Test
    void theSameNameIsFineInAnotherWorkspace() {
        UUID otherRuntime = runtimeService
                .register(mia.id(), otherWorkspace, "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();
        createByMia("Builder");

        AgentView there = service.create(mia.id(), otherWorkspace, new AgentDetails("Builder", "", otherRuntime, null));

        assertThat(there.workspaceId()).isEqualTo(otherWorkspace);
    }

    @Test
    void anOutsiderCannotCreateAnAgent() {
        assertThatThrownBy(() -> service.create(outsider.id(), workspace, details("Sneaky")))
                .isInstanceOf(NotFoundException.class);

        assertThat(agents.stored).isEmpty();
    }

    // ---- read --------------------------------------------------------------------------------------------

    @Test
    void membersListAndGetAgents_archivedOnesOnlyOnRequest() {
        AgentView active = createByMia("Active");
        AgentView gone = createByMia("Gone");
        service.archive(mia.id(), workspace, gone.id());

        assertThat(service.list(max.id(), workspace, false)).extracting(AgentView::name).containsExactly("Active");
        assertThat(service.list(max.id(), workspace, true)).extracting(AgentView::name).containsExactly("Active", "Gone");
        assertThat(service.get(max.id(), workspace, active.id()).name()).isEqualTo("Active");
        assertThat(service.get(max.id(), workspace, gone.id()).archived()).isTrue();
    }

    @Test
    void anOutsiderGetsNotFoundForReads() {
        AgentView agent = createByMia("Builder");

        assertThatThrownBy(() -> service.list(outsider.id(), workspace, false)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(outsider.id(), workspace, agent.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anAgentOfAnotherWorkspaceIsNotFoundEvenForSomeoneInBoth() {
        UUID otherRuntime = runtimeService
                .register(mia.id(), otherWorkspace, "mia-pc", "Mia's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();
        AgentView foreign = service.create(mia.id(), otherWorkspace, new AgentDetails("Foreign", "", otherRuntime, null));

        assertThatThrownBy(() -> service.get(mia.id(), workspace, foreign.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(mia.id(), workspace, foreign.id(), details("Hijack")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.archive(mia.id(), workspace, foreign.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theRuntimeShowsItsCurrentStatus() {
        AgentView agent = createByMia("Builder");
        clock.advance(Duration.ofMinutes(5));
        runtimeService.markStaleOffline();

        assertThat(service.get(mia.id(), workspace, agent.id()).runtime().status().code()).isEqualTo("offline");
    }

    // ---- update ------------------------------------------------------------------------------------------

    @Test
    void theCreatorEditsTheirAgent() {
        AgentView agent = createByMia("Builder");

        AgentView updated = service.update(
                mia.id(), workspace, agent.id(), new AgentDetails("Reviewer", "Review carefully.", miasRuntime, "opus"));

        assertThat(updated.name()).isEqualTo("Reviewer");
        assertThat(updated.instructions()).isEqualTo("Review carefully.");
        assertThat(updated.model()).isEqualTo("opus");
    }

    @Test
    void anAgentKeepsItsOwnNameWhenSavedUnchangedOrWithOtherCapitals() {
        AgentView agent = createByMia("Builder");

        assertThat(service.update(mia.id(), workspace, agent.id(), details("Builder")).name()).isEqualTo("Builder");
        assertThat(service.update(mia.id(), workspace, agent.id(), details("BUILDER")).name()).isEqualTo("BUILDER");
    }

    @Test
    void cannotRenameToTheNameOfAnotherActiveAgent() {
        createByMia("Builder");
        AgentView other = createByMia("Reviewer");

        assertThatThrownBy(() -> service.update(mia.id(), workspace, other.id(), details("builder")))
                .isInstanceOf(ConflictException.class);

        assertThat(service.get(mia.id(), workspace, other.id()).name()).isEqualTo("Reviewer");
    }

    @Test
    void anAdminAndAnOwnerEditAnyAgentWithoutChangingItsRuntime() {
        AgentView agent = createByMia("Builder");

        AgentView byAdmin = service.update(admin.id(), workspace, agent.id(), new AgentDetails("By admin", "x", miasRuntime, null));
        AgentView byOwner = service.update(owner.id(), workspace, agent.id(), new AgentDetails("By owner", "y", miasRuntime, null));

        assertThat(byAdmin.name()).isEqualTo("By admin");
        assertThat(byOwner.name()).isEqualTo("By owner");
        assertThat(byOwner.runtime().id()).isEqualTo(miasRuntime);
    }

    @Test
    void aPlainMemberCannotEditSomeoneElsesAgent() {
        AgentView agent = createByMia("Builder");

        assertThatThrownBy(() -> service.update(max.id(), workspace, agent.id(), details("Mine now")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("agent.manage_forbidden");
        assertThat(service.get(mia.id(), workspace, agent.id()).name()).isEqualTo("Builder");
    }

    @Test
    void moveToAnotherRuntimeNeedsARuntimeOfTheEditor() {
        AgentView agent = createByMia("Builder");
        UUID adminsRuntime = runtimeService
                .register(admin.id(), workspace, "adam-pc", "Adam's PC", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();
        UUID miasSecond = runtimeService
                .register(mia.id(), workspace, "mia-laptop", "Mia's laptop", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();

        // The creator may move it between their own runtimes, but not onto someone else's computer.
        assertThat(service.update(mia.id(), workspace, agent.id(), new AgentDetails("Builder", "", miasSecond, null))
                        .runtime().id())
                .isEqualTo(miasSecond);
        assertThatThrownBy(() -> service.update(mia.id(), workspace, agent.id(), new AgentDetails("Builder", "", maxsRuntime, null)))
                .isInstanceOf(ForbiddenException.class);
        // An admin may take the agent onto their own runtime, never onto another person's.
        assertThat(service.update(admin.id(), workspace, agent.id(), new AgentDetails("Builder", "", adminsRuntime, null))
                        .runtime().id())
                .isEqualTo(adminsRuntime);
        assertThatThrownBy(() -> service.update(admin.id(), workspace, agent.id(), new AgentDetails("Builder", "", miasRuntime, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anAnInvalidUpdateChangesNothing() {
        AgentView agent = createByMia("Builder");

        assertThatThrownBy(() -> service.update(mia.id(), workspace, agent.id(), new AgentDetails("", "new text", miasRuntime, null)))
                .isInstanceOf(ValidationException.class);

        AgentView stored = service.get(mia.id(), workspace, agent.id());
        assertThat(stored.name()).isEqualTo("Builder");
        assertThat(stored.instructions()).isEqualTo("Do the work.");
    }

    @Test
    void anOutsiderCannotUpdate() {
        AgentView agent = createByMia("Builder");

        assertThatThrownBy(() -> service.update(outsider.id(), workspace, agent.id(), details("X")))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- archive and restore -----------------------------------------------------------------------------

    @Test
    void theCreatorAdminsAndOwnersArchive() {
        AgentView first = createByMia("First");
        AgentView second = createByMia("Second");
        AgentView third = createByMia("Third");

        assertThat(service.archive(mia.id(), workspace, first.id()).archived()).isTrue();
        assertThat(service.archive(admin.id(), workspace, second.id()).archived()).isTrue();
        assertThat(service.archive(owner.id(), workspace, third.id()).archived()).isTrue();
        assertThat(service.list(mia.id(), workspace, false)).isEmpty();
    }

    @Test
    void archivingAnAgentCancelsItsTasksButArchivingTwiceDoesNotDoItAgain() {
        AgentView agent = createByMia("Builder");

        service.archive(mia.id(), workspace, agent.id());
        service.archive(mia.id(), workspace, agent.id());

        assertThat(taskControl.cancelled).containsExactly(new AgentId(agent.id()));
    }

    @Test
    void anAgentThatMovesToAnotherRuntimeTakesItsWaitingTasksAlong() {
        AgentView agent = createByMia("Builder");
        UUID second = runtimeService
                .register(mia.id(), workspace, "mia-laptop", "Mia's laptop", List.of(new ProviderInfo("claude", "1.0")))
                .getFirst().id();

        service.update(mia.id(), workspace, agent.id(), new AgentDetails("Builder", "", second, null));

        assertThat(taskControl.moved).containsEntry(new AgentId(agent.id()), new RuntimeId(second));
    }

    @Test
    void savingAnAgentOnTheSameRuntimeMovesNothing() {
        AgentView agent = createByMia("Builder");

        service.update(mia.id(), workspace, agent.id(), new AgentDetails("Builder", "New text", miasRuntime, null));

        assertThat(taskControl.moved).isEmpty();
    }

    @Test
    void archivingTwiceKeepsTheFirstTime() {
        AgentView agent = createByMia("Builder");
        AgentView first = service.archive(mia.id(), workspace, agent.id());
        clock.advance(Duration.ofHours(1));

        AgentView second = service.archive(mia.id(), workspace, agent.id());

        assertThat(second.archivedAt()).isEqualTo(first.archivedAt());
    }

    @Test
    void aPlainMemberCannotArchiveOrRestoreSomeoneElsesAgent() {
        AgentView agent = createByMia("Builder");

        assertThatThrownBy(() -> service.archive(max.id(), workspace, agent.id())).isInstanceOf(ForbiddenException.class);
        service.archive(mia.id(), workspace, agent.id());
        assertThatThrownBy(() -> service.restore(max.id(), workspace, agent.id())).isInstanceOf(ForbiddenException.class);
        assertThat(service.get(mia.id(), workspace, agent.id()).archived()).isTrue();
    }

    @Test
    void anArchivedAgentCannotBeEditedUntilRestored() {
        AgentView agent = createByMia("Builder");
        service.archive(mia.id(), workspace, agent.id());

        assertThatThrownBy(() -> service.update(mia.id(), workspace, agent.id(), details("New")))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("agent.archived");

        service.restore(mia.id(), workspace, agent.id());
        assertThat(service.update(mia.id(), workspace, agent.id(), details("New")).name()).isEqualTo("New");
    }

    @Test
    void anArchivedNameCanBeUsedAgainButTheOldAgentThenCannotComeBack() {
        AgentView old = createByMia("Builder");
        service.archive(mia.id(), workspace, old.id());
        AgentView replacement = createByMia("Builder");

        assertThat(replacement.id()).isNotEqualTo(old.id());
        assertThatThrownBy(() -> service.restore(mia.id(), workspace, old.id()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("agent.name_taken");
        assertThat(service.get(mia.id(), workspace, old.id()).archived()).isTrue();
    }

    // ---- the directory other contexts use ----------------------------------------------------------------

    @Test
    void theDirectoryAnswersWithNamesAndArchivedFlags() {
        AgentView active = createByMia("Active");
        AgentView gone = createByMia("Gone");
        service.archive(mia.id(), workspace, gone.id());

        assertThat(directory.find(workspace, active.id()))
                .contains(new AgentSummary(active.id(), workspace, "Active", false));
        assertThat(directory.find(workspace, gone.id())).get().extracting(AgentSummary::archived).isEqualTo(true);
        assertThat(directory.findByIds(workspace, List.of(active.id(), gone.id(), UUID.randomUUID())))
                .containsOnlyKeys(active.id(), gone.id());
        assertThat(directory.findByIds(workspace, List.of())).isEqualTo(Map.of());
    }

    @Test
    void theDirectoryNeverShowsAnAgentOfAnotherWorkspace() {
        AgentView agent = createByMia("Builder");

        assertThat(directory.find(otherWorkspace, agent.id())).isEmpty();
        assertThat(directory.findByIds(otherWorkspace, List.of(agent.id()))).isEmpty();
        assertThat(directory.find(workspace, UUID.randomUUID())).isEmpty();
    }
}
