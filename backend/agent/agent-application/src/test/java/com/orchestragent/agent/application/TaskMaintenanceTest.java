package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.agent.application.Fakes.FakeAccess;
import com.orchestragent.agent.application.Fakes.FakeUsers;
import com.orchestragent.agent.application.Fakes.InMemoryRuntimes;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.TaskFakes.InMemoryTasks;
import com.orchestragent.agent.application.TaskFakes.Published;
import com.orchestragent.agent.contract.AgentTaskChanged;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.agent.domain.AgentId;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeStatus;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskTrigger;
import com.orchestragent.workspace.contract.WorkspaceDirectory;
import com.orchestragent.workspace.contract.WorkspaceRef;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What keeps tasks from getting stuck when a daemon goes quiet or away. */
class TaskMaintenanceTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryRuntimes runtimes = new InMemoryRuntimes();
    private final InMemoryTasks tasks = new InMemoryTasks(TaskFakes.statusOf(runtimes));
    private final Published published = new Published();
    private final TaskMaintenance maintenance = new TaskMaintenance(tasks, new TaskEvents(published, clock), clock);
    private final FakeAccess access = new FakeAccess();
    private final FakeUsers users = new FakeUsers();
    private final RuntimeService runtimeService = new RuntimeService(runtimes, access, users, clock);
    private final WorkspaceDirectory workspaces = userId -> List.of(new WorkspaceRef(UUID.randomUUID(), "W", "w"));
    private final DaemonService daemon = new DaemonService(workspaces, runtimeService, runtimes, maintenance);

    private final UUID workspace = UUID.randomUUID();
    private final UUID ada = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Runtime adasRuntime;
    private final Runtime bobsRuntime;

    {
        access.join(workspace, ada, WorkspaceRole.MEMBER);
        access.join(workspace, bob, WorkspaceRole.MEMBER);
        adasRuntime = TaskFakes.runtime(runtimes, workspace, ada, "ada-pc", clock.instant());
        bobsRuntime = TaskFakes.runtime(runtimes, workspace, bob, "bob-pc", clock.instant());
    }

    private AgentTask queued(Runtime on, UUID issueId) {
        AgentTask task = AgentTask.queue(
                workspace, issueId, AgentId.generate(), on.id(), TaskTrigger.ASSIGNED, null, UUID.randomUUID(), 1, clock.instant());
        tasks.save(task);
        return task;
    }

    private AgentTask dispatched(Runtime on, UUID issueId) {
        AgentTask task = queued(on, issueId);
        task.dispatch(clock.instant());
        return task;
    }

    private AgentTask running(Runtime on, UUID issueId) {
        AgentTask task = dispatched(on, issueId);
        task.start(clock.instant());
        return task;
    }

    // ---- leases ------------------------------------------------------------------------------------------

    @Test
    void aTaskTheDaemonDidNotStartInTimeGoesBackToTheQueue() {
        AgentTask task = dispatched(adasRuntime, UUID.randomUUID());
        clock.advance(Duration.ofSeconds(61));

        int count = maintenance.requeueExpiredLeases();

        assertThat(count).isEqualTo(1);
        assertThat(task.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.leaseExpiresAt()).isNull();
        assertThat(published.of(AgentTaskChanged.class)).singleElement().satisfies(event -> assertThat(event.status()).isEqualTo("queued"));
    }

    @Test
    void aTaskWithinItsLeaseIsLeftAlone() {
        AgentTask task = dispatched(adasRuntime, UUID.randomUUID());
        clock.advance(Duration.ofSeconds(60));

        assertThat(maintenance.requeueExpiredLeases()).isZero();

        assertThat(task.status()).isEqualTo(TaskStatus.DISPATCHED);
    }

    @Test
    void queuedRunningAndFinishedTasksAreNotTouchedByTheLeaseJob() {
        AgentTask waiting = queued(adasRuntime, UUID.randomUUID());
        AgentTask working = running(adasRuntime, UUID.randomUUID());
        clock.advance(Duration.ofHours(1));

        assertThat(maintenance.requeueExpiredLeases()).isZero();

        assertThat(waiting.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(working.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void anExpiredTaskIsCancelledInsteadWhenAnotherTaskOfTheIssueIsAlreadyWaiting() {
        UUID issueId = UUID.randomUUID();
        AgentTask expired = dispatched(adasRuntime, issueId);
        AgentTask newer = queued(adasRuntime, issueId);
        clock.advance(Duration.ofSeconds(120));

        maintenance.requeueExpiredLeases();

        assertThat(expired.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(newer.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(tasks.stored.values().stream().filter(task -> task.status() == TaskStatus.QUEUED)).hasSize(1);
    }

    @Test
    void severalExpiredTasksAreAllHandledInOnePass() {
        AgentTask first = dispatched(adasRuntime, UUID.randomUUID());
        AgentTask second = dispatched(bobsRuntime, UUID.randomUUID());
        clock.advance(Duration.ofSeconds(90));

        assertThat(maintenance.requeueExpiredLeases()).isEqualTo(2);

        assertThat(first.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(second.status()).isEqualTo(TaskStatus.QUEUED);
    }

    // ---- runtimes that went offline ----------------------------------------------------------------------

    @Test
    void aRunningTaskOfARuntimeThatWentOfflineFailsWithAReasonAndIsAnnounced() {
        AgentTask task = running(adasRuntime, UUID.randomUUID());
        clock.advance(Duration.ofMinutes(5));
        runtimeService.markStaleOffline();
        assertThat(adasRuntime.status()).isEqualTo(RuntimeStatus.OFFLINE);

        int count = maintenance.failTasksOfOfflineRuntimes();

        assertThat(count).isEqualTo(1);
        assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.failureReason()).isEqualTo("runtime_offline");
        assertThat(task.error()).isEqualTo(TaskMaintenance.RUNTIME_OFFLINE_MESSAGE);
        assertThat(published.of(AgentTaskFailed.class)).singleElement().satisfies(event -> {
            assertThat(event.failureReason()).isEqualTo("runtime_offline");
            assertThat(event.taskId()).isEqualTo(task.id().value());
        });
    }

    @Test
    void tasksOfRuntimesThatAreStillOnlineKeepRunning() {
        AgentTask mine = running(adasRuntime, UUID.randomUUID());
        AgentTask theirs = running(bobsRuntime, UUID.randomUUID());
        clock.advance(Duration.ofSeconds(60));
        runtimeService.heartbeat(bob, List.of(bobsRuntime.id().value()));
        clock.advance(Duration.ofSeconds(60));
        runtimeService.markStaleOffline();

        assertThat(maintenance.failTasksOfOfflineRuntimes()).isEqualTo(1);

        assertThat(mine.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(theirs.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void theOfflineJobDoesNotTouchWaitingOrClaimedTasks() {
        AgentTask waiting = queued(adasRuntime, UUID.randomUUID());
        AgentTask claimed = dispatched(adasRuntime, UUID.randomUUID());
        clock.advance(Duration.ofMinutes(5));
        runtimeService.markStaleOffline();

        assertThat(maintenance.failTasksOfOfflineRuntimes()).isZero();

        assertThat(waiting.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(claimed.status()).isEqualTo(TaskStatus.DISPATCHED);
    }

    @Test
    void failingTwiceDoesNotFailAgain() {
        running(adasRuntime, UUID.randomUUID());
        clock.advance(Duration.ofMinutes(5));
        runtimeService.markStaleOffline();
        maintenance.failTasksOfOfflineRuntimes();

        assertThat(maintenance.failTasksOfOfflineRuntimes()).isZero();
        assertThat(published.of(AgentTaskFailed.class)).hasSize(1);
    }

    // ---- a daemon shutting down --------------------------------------------------------------------------

    @Test
    void whenADaemonDeregistersItsRuntimesGoOfflineAndItsRunningTasksFail() {
        AgentTask running = running(adasRuntime, UUID.randomUUID());
        AgentTask waiting = queued(adasRuntime, UUID.randomUUID());

        daemon.deregister(ada, List.of(adasRuntime.id().value()));

        assertThat(adasRuntime.status()).isEqualTo(RuntimeStatus.OFFLINE);
        assertThat(running.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(running.failureReason()).isEqualTo("runtime_offline");
        assertThat(waiting.status()).as("waiting work stays for when the daemon is back").isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void aDaemonCannotShutDownSomeoneElsesRuntimesOrFailTheirTasks() {
        AgentTask theirs = running(bobsRuntime, UUID.randomUUID());

        daemon.deregister(ada, List.of(bobsRuntime.id().value()));

        assertThat(bobsRuntime.status()).isEqualTo(RuntimeStatus.ONLINE);
        assertThat(theirs.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void deregisteringNothingChangesNothing() {
        AgentTask running = running(adasRuntime, UUID.randomUUID());

        daemon.deregister(ada, List.of());

        assertThat(running.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void theDaemonSeesTheWorkspacesOfTheTokenOwner() {
        assertThat(daemon.workspaces(ada)).hasSize(1);
    }
}
