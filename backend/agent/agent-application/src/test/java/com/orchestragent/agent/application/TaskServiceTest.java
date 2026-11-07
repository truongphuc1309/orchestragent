package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.agent.application.Fakes.FakeAccess;
import com.orchestragent.agent.application.Fakes.InMemoryAgents;
import com.orchestragent.agent.application.Fakes.InMemoryRuntimes;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.TaskFakes.FakeIssues;
import com.orchestragent.agent.application.TaskFakes.InMemoryMessages;
import com.orchestragent.agent.application.TaskFakes.InMemoryTasks;
import com.orchestragent.agent.application.TaskFakes.Published;
import com.orchestragent.agent.application.TaskViews.TaskMessageView;
import com.orchestragent.agent.application.TaskViews.TaskView;
import com.orchestragent.agent.contract.AgentTaskChanged;
import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.MessageType;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.TaskMessage;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskTrigger;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What people do with tasks in the web app: look, cancel, retry. */
class TaskServiceTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryRuntimes runtimes = new InMemoryRuntimes();
    private final InMemoryAgents agents = new InMemoryAgents();
    private final InMemoryTasks tasks = new InMemoryTasks(TaskFakes.statusOf(runtimes));
    private final InMemoryMessages messages = new InMemoryMessages();
    private final FakeIssues issues = new FakeIssues();
    private final FakeAccess access = new FakeAccess();
    private final Published published = new Published();
    private final TaskService service =
            new TaskService(tasks, messages, agents, runtimes, issues, access, new TaskEvents(published, clock), clock);

    private final UUID workspace = UUID.randomUUID();
    private final UUID otherWorkspace = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final Runtime runtime;
    private final Agent builder;
    private final IssueDetails issue;

    {
        access.join(workspace, member, WorkspaceRole.MEMBER);
        access.join(otherWorkspace, outsider, WorkspaceRole.OWNER);
        runtime = TaskFakes.runtime(runtimes, workspace, member, "pc", clock.instant());
        builder = Agent.create(workspace, "Builder", "Do it.", runtime.id(), null, member, clock.instant());
        agents.save(builder);
        issue = issues.add(workspace, 1, ActorType.AGENT, builder.id().value(), "todo");
    }

    private AgentTask task(TaskTrigger trigger, int attempt) {
        clock.advance(Duration.ofSeconds(1));
        AgentTask task = AgentTask.queue(workspace, issue.id(), builder.id(), runtime.id(), trigger, null, UUID.randomUUID(), attempt, clock.instant());
        tasks.save(task);
        return task;
    }

    private AgentTask failedTask(int attempt) {
        AgentTask task = task(TaskTrigger.ASSIGNED, attempt);
        task.dispatch(clock.instant());
        task.start(clock.instant());
        task.fail("The tool crashed", "agent_error", null, null, clock.instant());
        return task;
    }

    // ---- looking -----------------------------------------------------------------------------------------

    @Test
    void listsTheRunsOfAnIssueNewestFirstWithAgentRuntimeAndCost() {
        AgentTask first = task(TaskTrigger.ASSIGNED, 1);
        first.dispatch(clock.instant());
        first.start(clock.instant());
        first.complete("Done", "s1", "/w", new com.orchestragent.agent.domain.TaskUsage(10L, 5L, new BigDecimal("0.01")), clock.instant());
        AgentTask second = task(TaskTrigger.COMMENT, 1);

        List<TaskView> views = service.listForIssue(member, workspace, issue.id());

        assertThat(views).extracting(TaskView::id).containsExactly(second.id().value(), first.id().value());
        TaskView newest = views.get(0);
        assertThat(newest.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(newest.trigger()).isEqualTo(TaskTrigger.COMMENT);
        assertThat(newest.agentName()).isEqualTo("Builder");
        assertThat(newest.runtime().deviceName()).isEqualTo("Device pc");
        TaskView oldest = views.get(1);
        assertThat(oldest.inputTokens()).isEqualTo(10);
        assertThat(oldest.outputTokens()).isEqualTo(5);
        assertThat(oldest.costUsd()).isEqualByComparingTo("0.01");
        assertThat(oldest.startedAt()).isNotNull();
        assertThat(oldest.finishedAt()).isNotNull();
    }

    @Test
    void onlyTheTasksOfThatIssueAreListed() {
        task(TaskTrigger.ASSIGNED, 1);
        IssueDetails other = issues.add(workspace, 2, ActorType.AGENT, builder.id().value(), "todo");
        AgentTask elsewhere = AgentTask.queue(workspace, other.id(), builder.id(), runtime.id(), TaskTrigger.ASSIGNED, null, UUID.randomUUID(), 1, clock.instant());
        tasks.save(elsewhere);

        assertThat(service.listForIssue(member, workspace, issue.id())).hasSize(1);
    }

    @Test
    void anOutsiderCannotListOrReadAnything() {
        AgentTask task = task(TaskTrigger.ASSIGNED, 1);

        assertThatThrownBy(() -> service.listForIssue(outsider, workspace, issue.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.messages(outsider, workspace, task.id().value(), -1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(outsider, workspace, task.id().value())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.retry(outsider, workspace, task.id().value())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anIssueOrTaskOfAnotherWorkspaceIsNotFound() {
        access.join(otherWorkspace, member, WorkspaceRole.MEMBER);
        AgentTask task = task(TaskTrigger.ASSIGNED, 1);

        assertThatThrownBy(() -> service.listForIssue(member, otherWorkspace, issue.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.messages(member, otherWorkspace, task.id().value(), -1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(member, otherWorkspace, task.id().value())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.retry(member, otherWorkspace, task.id().value())).isInstanceOf(NotFoundException.class);
        assertThat(task.status()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void readsTheLogAfterAGivenNumber() {
        AgentTask task = task(TaskTrigger.ASSIGNED, 1);
        Instant now = clock.instant();
        messages.append(task.id(), List.of(
                new TaskMessage(0, MessageType.TEXT, "one", now),
                new TaskMessage(1, MessageType.TOOL_USE, "two", now),
                new TaskMessage(2, MessageType.TOOL_RESULT, "three", now)));

        assertThat(service.messages(member, workspace, task.id().value(), -1)).extracting(TaskMessageView::content)
                .containsExactly("one", "two", "three");
        assertThat(service.messages(member, workspace, task.id().value(), 0)).extracting(TaskMessageView::seq).containsExactly(1L, 2L);
        assertThat(service.messages(member, workspace, task.id().value(), 2)).isEmpty();
    }

    // ---- cancelling --------------------------------------------------------------------------------------

    @Test
    void cancellingAWaitingOrRunningTaskStopsItAndAnnouncesIt() {
        AgentTask waiting = task(TaskTrigger.ASSIGNED, 1);
        AgentTask working = failedTask(1);
        AgentTask running = task(TaskTrigger.COMMENT, 1);
        running.dispatch(clock.instant());
        running.start(clock.instant());
        published.clear();

        assertThat(service.cancel(member, workspace, waiting.id().value()).status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(service.cancel(member, workspace, running.id().value()).status()).isEqualTo(TaskStatus.CANCELLED);

        assertThat(working.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(published.of(AgentTaskChanged.class)).hasSize(2).allSatisfy(event -> assertThat(event.status()).isEqualTo("cancelled"));
    }

    @Test
    void cancellingAFinishedTaskChangesNothing() {
        AgentTask failed = failedTask(1);
        published.clear();

        TaskView view = service.cancel(member, workspace, failed.id().value());

        assertThat(view.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(published).isEmpty();
    }

    // ---- retrying ----------------------------------------------------------------------------------------

    @Test
    void retryingAFailedTaskQueuesANewAttemptOnTheAgentsCurrentRuntime() {
        AgentTask failed = failedTask(1);
        Runtime newer = TaskFakes.runtime(runtimes, workspace, member, "new-pc", clock.instant());
        builder.changeRuntime(newer.id());

        TaskView retry = service.retry(member, workspace, failed.id().value());

        assertThat(retry.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(retry.attempt()).isEqualTo(2);
        assertThat(retry.trigger()).isEqualTo(TaskTrigger.RETRY);
        assertThat(retry.id()).isNotEqualTo(failed.id().value());
        AgentTask stored = tasks.findById(new com.orchestragent.agent.domain.TaskId(retry.id())).orElseThrow();
        assertThat(stored.runtimeId()).isEqualTo(newer.id());
        assertThat(stored.sourceEventId()).isNull();
        assertThat(failed.status()).as("the failed task stays as history").isEqualTo(TaskStatus.FAILED);
        assertThat(published.of(AgentTaskChanged.class)).anySatisfy(event -> assertThat(event.status()).isEqualTo("queued"));
    }

    @Test
    void theAttemptNumberKeepsCountingAcrossRetries() {
        AgentTask failed = failedTask(3);

        assertThat(service.retry(member, workspace, failed.id().value()).attempt()).isEqualTo(4);
    }

    @Test
    void onlyAFailedTaskCanBeRetried() {
        AgentTask waiting = task(TaskTrigger.ASSIGNED, 1);
        AgentTask cancelled = task(TaskTrigger.COMMENT, 1);
        cancelled.cancel(clock.instant());

        for (AgentTask task : List.of(waiting, cancelled)) {
            assertThatThrownBy(() -> service.retry(member, workspace, task.id().value()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code")
                    .isEqualTo("task.not_failed");
        }
    }

    @Test
    void noRetryWhileAnotherRunIsAlreadyWaiting() {
        AgentTask failed = failedTask(1);
        task(TaskTrigger.COMMENT, 1);

        assertThatThrownBy(() -> service.retry(member, workspace, failed.id().value()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("task.already_queued");
    }

    @Test
    void noRetryForAnArchivedAgent() {
        AgentTask failed = failedTask(1);
        builder.archive(clock.instant());

        assertThatThrownBy(() -> service.retry(member, workspace, failed.id().value()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("agent.archived");
    }

    @Test
    void noRetryWhenTheIssueBelongsToSomeoneElseNow() {
        AgentTask failed = failedTask(1);
        issues.assign(issue, ActorType.MEMBER, member);

        assertThatThrownBy(() -> service.retry(member, workspace, failed.id().value()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("task.agent_not_assigned");
        assertThat(tasks.stored).hasSize(1);
    }

    @Test
    void noRetryForADeletedIssue() {
        AgentTask failed = failedTask(1);
        issues.byId.remove(issue.id());

        assertThatThrownBy(() -> service.retry(member, workspace, failed.id().value())).isInstanceOf(NotFoundException.class);
    }

    // ---- what changing an agent does to its tasks --------------------------------------------------------

    @Test
    void archivingAnAgentCancelsAllItsActiveTasks() {
        AgentTask waiting = task(TaskTrigger.ASSIGNED, 1);
        IssueDetails other = issues.add(workspace, 2, ActorType.AGENT, builder.id().value(), "todo");
        AgentTask running = AgentTask.queue(workspace, other.id(), builder.id(), runtime.id(), TaskTrigger.ASSIGNED, null, UUID.randomUUID(), 1, clock.instant());
        running.dispatch(clock.instant());
        running.start(clock.instant());
        tasks.save(running);
        AgentTask done = failedTask(1);

        service.cancelAllTasksOf(builder.id());

        assertThat(waiting.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(running.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(done.status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void anAgentThatMovesTakesOnlyItsWaitingTasksAlong() {
        AgentTask waiting = task(TaskTrigger.ASSIGNED, 1);
        IssueDetails other = issues.add(workspace, 2, ActorType.AGENT, builder.id().value(), "todo");
        AgentTask running = AgentTask.queue(workspace, other.id(), builder.id(), runtime.id(), TaskTrigger.ASSIGNED, null, UUID.randomUUID(), 1, clock.instant());
        running.dispatch(clock.instant());
        running.start(clock.instant());
        tasks.save(running);
        Runtime newer = TaskFakes.runtime(runtimes, workspace, member, "new-pc", clock.instant());

        service.moveQueuedTasks(builder.id(), newer.id());

        assertThat(waiting.runtimeId()).isEqualTo(newer.id());
        assertThat(running.runtimeId()).isEqualTo(runtime.id());
    }
}
