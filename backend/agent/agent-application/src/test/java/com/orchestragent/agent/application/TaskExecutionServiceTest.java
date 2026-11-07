package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.agent.application.Fakes.FakeAccess;
import com.orchestragent.agent.application.Fakes.InMemoryAgents;
import com.orchestragent.agent.application.Fakes.InMemoryRuntimes;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.TaskExecutionService.CompletionInput;
import com.orchestragent.agent.application.TaskExecutionService.FailureInput;
import com.orchestragent.agent.application.TaskExecutionService.MessageInput;
import com.orchestragent.agent.application.TaskFakes.FakeIssues;
import com.orchestragent.agent.application.TaskFakes.InMemoryMessages;
import com.orchestragent.agent.application.TaskFakes.InMemoryTasks;
import com.orchestragent.agent.application.TaskFakes.Published;
import com.orchestragent.agent.application.TaskViews.ClaimedTask;
import com.orchestragent.agent.contract.AgentTaskChanged;
import com.orchestragent.agent.contract.AgentTaskCompleted;
import com.orchestragent.agent.contract.AgentTaskFailed;
import com.orchestragent.agent.contract.AgentTaskMessagesAppended;
import com.orchestragent.agent.contract.AgentTaskStarted;
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
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The daemon's side of tasks: claiming, progress reports and finishing, with the focus on who may touch what. */
class TaskExecutionServiceTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryRuntimes runtimes = new InMemoryRuntimes();
    private final InMemoryAgents agents = new InMemoryAgents();
    private final InMemoryTasks tasks = new InMemoryTasks(TaskFakes.statusOf(runtimes));
    private final InMemoryMessages messages = new InMemoryMessages();
    private final FakeIssues issues = new FakeIssues();
    private final FakeAccess access = new FakeAccess();
    private final Published published = new Published();
    private final TaskExecutionService service =
            new TaskExecutionService(tasks, messages, agents, runtimes, issues, access, new TaskEvents(published, clock), clock);

    private final UUID workspace = UUID.randomUUID();
    private final UUID ada = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Runtime adasRuntime;
    private final Runtime bobsRuntime;
    private final Agent builder;
    private final IssueDetails issue;

    {
        access.join(workspace, ada, WorkspaceRole.MEMBER);
        access.join(workspace, bob, WorkspaceRole.MEMBER);
        adasRuntime = TaskFakes.runtime(runtimes, workspace, ada, "ada-pc", clock.instant());
        bobsRuntime = TaskFakes.runtime(runtimes, workspace, bob, "bob-pc", clock.instant());
        builder = Agent.create(workspace, "Builder", "Write clean code.", adasRuntime.id(), "sonnet", ada, clock.instant());
        agents.save(builder);
        issue = issues.add(workspace, 7, ActorType.AGENT, builder.id().value(), "todo");
        issues.comment(issue, ActorType.MEMBER, "Ada", "Please fix the login.", clock.instant());
    }

    private AgentTask queueTask() {
        return queueTask(issue, builder, adasRuntime);
    }

    private AgentTask queueTask(IssueDetails forIssue, Agent agent, Runtime on) {
        clock.advance(Duration.ofSeconds(1));
        AgentTask task = AgentTask.queue(
                workspace, forIssue.id(), agent.id(), on.id(), TaskTrigger.ASSIGNED, null, UUID.randomUUID(), 1, clock.instant());
        tasks.save(task);
        return task;
    }

    private AgentTask claimed() {
        AgentTask task = queueTask();
        service.claim(ada, List.of(adasRuntime.id().value()), 5);
        return task;
    }

    private AgentTask running() {
        AgentTask task = claimed();
        service.start(ada, task.id().value());
        return task;
    }

    private static MessageInput line(long seq, String type, String content) {
        return new MessageInput(seq, type, content);
    }

    // ---- claim -------------------------------------------------------------------------------------------

    @Test
    void claimingHandsOverEverythingTheDaemonNeedsToBriefTheAgent() {
        AgentTask task = queueTask();

        List<ClaimedTask> claimed = service.claim(ada, List.of(adasRuntime.id().value()), 5);

        assertThat(claimed).singleElement().satisfies(brief -> {
            assertThat(brief.taskId()).isEqualTo(task.id().value());
            assertThat(brief.workspaceId()).isEqualTo(workspace);
            assertThat(brief.runtimeId()).isEqualTo(adasRuntime.id().value());
            assertThat(brief.provider()).isEqualTo("claude");
            assertThat(brief.trigger()).isEqualTo(TaskTrigger.ASSIGNED);
            assertThat(brief.attempt()).isEqualTo(1);
            assertThat(brief.agent().name()).isEqualTo("Builder");
            assertThat(brief.agent().instructions()).isEqualTo("Write clean code.");
            assertThat(brief.agent().model()).isEqualTo("sonnet");
            assertThat(brief.issue().number()).isEqualTo(7);
            assertThat(brief.issue().title()).isEqualTo("Issue 7");
            assertThat(brief.issue().description()).isEqualTo("Description 7");
            assertThat(brief.comments()).singleElement().satisfies(comment -> {
                assertThat(comment.authorType()).isEqualTo("member");
                assertThat(comment.authorName()).isEqualTo("Ada");
                assertThat(comment.body()).isEqualTo("Please fix the login.");
            });
            assertThat(brief.priorSessionId()).isNull();
            assertThat(brief.priorWorkDir()).isNull();
        });
        assertThat(task.status()).isEqualTo(TaskStatus.DISPATCHED);
        assertThat(task.leaseExpiresAt()).isEqualTo(clock.instant().plusSeconds(60));
        assertThat(published.of(AgentTaskChanged.class)).anySatisfy(event -> assertThat(event.status()).isEqualTo("dispatched"));
    }

    @Test
    void anAgentThatWorkedOnTheIssueBeforeResumesItsSessionInTheSameFolder() {
        AgentTask first = queueTask();
        service.claim(ada, List.of(adasRuntime.id().value()), 5);
        service.start(ada, first.id().value());
        service.complete(ada, first.id().value(), new CompletionInput("Done", "session-1", "/work/7", null, null, null));
        queueTask();

        List<ClaimedTask> second = service.claim(ada, List.of(adasRuntime.id().value()), 5);

        assertThat(second).singleElement().satisfies(brief -> {
            assertThat(brief.priorSessionId()).isEqualTo("session-1");
            assertThat(brief.priorWorkDir()).isEqualTo("/work/7");
        });
    }

    @Test
    void aFailedEarlierRunLeavesNothingToResume() {
        AgentTask first = queueTask();
        service.claim(ada, List.of(adasRuntime.id().value()), 5);
        service.start(ada, first.id().value());
        service.fail(ada, first.id().value(), new FailureInput("boom", "agent_error", "session-x", "/work/x"));
        queueTask();

        ClaimedTask second = service.claim(ada, List.of(adasRuntime.id().value()), 5).getFirst();

        assertThat(second.priorSessionId()).isNull();
    }

    @Test
    void aDaemonOnlyGetsTasksOfItsOwnRuntimes() {
        queueTask(issue, builder, adasRuntime);

        assertThat(service.claim(bob, List.of(adasRuntime.id().value()), 5)).isEmpty();
        assertThat(service.claim(bob, List.of(bobsRuntime.id().value()), 5)).isEmpty();
        assertThat(service.claim(ada, List.of(bobsRuntime.id().value()), 5)).isEmpty();
        assertThat(service.claim(ada, List.of(UUID.randomUUID()), 5)).isEmpty();
        assertThat(tasks.stored.values()).allSatisfy(task -> assertThat(task.status()).isEqualTo(TaskStatus.QUEUED));
    }

    @Test
    void aPersonWhoLeftTheWorkspaceGetsNoMoreWork() {
        FakeAccess nobodyIsMember = new FakeAccess();
        TaskExecutionService strict = new TaskExecutionService(
                tasks, messages, agents, runtimes, issues, nobodyIsMember, new TaskEvents(published, clock), clock);
        AgentTask task = queueTask();

        assertThat(strict.claim(ada, List.of(adasRuntime.id().value()), 5)).isEmpty();
        assertThat(task.status()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void claimingNeverHandsOutTheSameTaskTwice() {
        queueTask();

        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 5)).hasSize(1);
        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 5)).isEmpty();
    }

    @Test
    void claimingRespectsTheMaximumAndTheOldestComeFirst() {
        List<IssueDetails> several = new ArrayList<>();
        List<AgentTask> queued = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            IssueDetails other = issues.add(workspace, 100 + i, ActorType.AGENT, builder.id().value(), "todo");
            several.add(other);
            queued.add(queueTask(other, builder, adasRuntime));
        }

        List<ClaimedTask> first = service.claim(ada, List.of(adasRuntime.id().value()), 2);

        assertThat(first).extracting(ClaimedTask::taskId).containsExactly(queued.get(0).id().value(), queued.get(1).id().value());
        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 0)).as("at least one").hasSize(1);
        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 1000)).as("the rest").hasSize(1);
    }

    @Test
    void aTaskWaitsWhileAnotherRunOfTheSameIssueIsInProgress() {
        AgentTask first = running();
        AgentTask second = queueTask();

        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 5)).isEmpty();
        assertThat(second.status()).isEqualTo(TaskStatus.QUEUED);

        service.complete(ada, first.id().value(), new CompletionInput("Done", null, null, null, null, null));
        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 5)).hasSize(1);
    }

    @Test
    void aVanishedIssueMeansNothingToRunAndTheTaskIsCancelled() {
        AgentTask forIssueGone = queueTask();
        issues.byId.remove(issue.id());

        assertThat(service.claim(ada, List.of(adasRuntime.id().value()), 5)).isEmpty();

        assertThat(forIssueGone.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(published.of(AgentTaskChanged.class)).anySatisfy(event -> assertThat(event.status()).isEqualTo("cancelled"));
    }

    @Test
    void askingForTooManyRuntimesIsRefused() {
        List<UUID> many = java.util.stream.Stream.generate(UUID::randomUUID).limit(51).toList();

        assertThatThrownBy(() -> service.claim(ada, many, 5)).isInstanceOf(ValidationException.class);
    }

    // ---- start -------------------------------------------------------------------------------------------

    @Test
    void startingMovesTheTaskToRunningAndAnnouncesIt() {
        AgentTask task = claimed();

        TaskStatus status = service.start(ada, task.id().value());

        assertThat(status).isEqualTo(TaskStatus.RUNNING);
        assertThat(task.startedAt()).isEqualTo(clock.instant());
        assertThat(published.of(AgentTaskStarted.class)).singleElement().satisfies(event -> {
            assertThat(event.taskId()).isEqualTo(task.id().value());
            assertThat(event.issueId()).isEqualTo(issue.id());
            assertThat(event.agentId()).isEqualTo(builder.id().value());
        });
    }

    @Test
    void startingTwiceIsHarmlessAndAnnouncesOnce() {
        AgentTask task = claimed();
        service.start(ada, task.id().value());

        assertThat(service.start(ada, task.id().value())).isEqualTo(TaskStatus.RUNNING);

        assertThat(published.of(AgentTaskStarted.class)).hasSize(1);
    }

    @Test
    void aCancelledTaskIsNotStartedAndTheDaemonLearnsWhy() {
        AgentTask task = claimed();
        task.cancel(clock.instant());

        assertThat(service.start(ada, task.id().value())).isEqualTo(TaskStatus.CANCELLED);

        assertThat(task.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(published.of(AgentTaskStarted.class)).isEmpty();
    }

    @Test
    void aTaskThatWentBackToTheQueueCannotBeStartedByTheOldClaimant() {
        AgentTask task = queueTask();

        assertThatThrownBy(() -> service.start(ada, task.id().value()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("task.lease_lost");
    }

    // ---- messages ----------------------------------------------------------------------------------------

    @Test
    void storesTheLogLinesInOrderAndAnnouncesTheNewestNumber() {
        AgentTask task = running();

        TaskStatus status = service.appendMessages(
                ada, task.id().value(), List.of(line(0, "text", "Looking at the code"), line(1, "tool_use", "grep login")));

        assertThat(status).isEqualTo(TaskStatus.RUNNING);
        assertThat(messages.findAfter(task.id(), -1, 100))
                .extracting(TaskMessage::seq, TaskMessage::type, TaskMessage::content)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(0L, MessageType.TEXT, "Looking at the code"),
                        org.assertj.core.api.Assertions.tuple(1L, MessageType.TOOL_USE, "grep login"));
        assertThat(published.of(AgentTaskMessagesAppended.class)).singleElement().satisfies(event -> {
            assertThat(event.lastSeq()).isEqualTo(1);
            assertThat(event.taskId()).isEqualTo(task.id().value());
        });
    }

    @Test
    void resendingABatchStoresNothingTwiceAndAnnouncesNothing() {
        AgentTask task = running();
        List<MessageInput> batch = List.of(line(0, "text", "one"), line(1, "text", "two"));
        service.appendMessages(ada, task.id().value(), batch);
        published.clear();

        service.appendMessages(ada, task.id().value(), batch);

        assertThat(messages.findAfter(task.id(), -1, 100)).hasSize(2);
        assertThat(published.of(AgentTaskMessagesAppended.class)).isEmpty();
    }

    @Test
    void aBatchWithTheSameNumberTwiceKeepsTheFirst() {
        AgentTask task = running();

        service.appendMessages(ada, task.id().value(), List.of(line(0, "text", "first"), line(0, "text", "second")));

        assertThat(messages.findAfter(task.id(), -1, 100)).extracting(TaskMessage::content).containsExactly("first");
    }

    @Test
    void overlongContentIsCut() {
        AgentTask task = running();

        service.appendMessages(ada, task.id().value(), List.of(line(0, "tool_result", "x".repeat(TaskMessage.MAX_CONTENT_LENGTH + 100))));

        assertThat(messages.findAfter(task.id(), -1, 1).getFirst().content()).hasSize(TaskMessage.MAX_CONTENT_LENGTH);
    }

    @Test
    void anUnknownTypeOrATooLargeBatchIsRefusedAndNothingIsStored() {
        AgentTask task = running();
        List<MessageInput> tooMany = java.util.stream.LongStream.range(0, 201).mapToObj(i -> line(i, "text", "x")).toList();

        assertThatThrownBy(() -> service.appendMessages(ada, task.id().value(), List.of(line(0, "shout", "x"))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.appendMessages(ada, task.id().value(), tooMany)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.appendMessages(ada, task.id().value(), List.of(line(-1, "text", "x"))))
                .isInstanceOf(ValidationException.class);

        assertThat(messages.findAfter(task.id(), -1, 100)).isEmpty();
    }

    @Test
    void messagesAfterTheTaskWasCancelledAreDroppedQuietly() {
        AgentTask task = running();
        task.cancel(clock.instant());

        TaskStatus status = service.appendMessages(ada, task.id().value(), List.of(line(0, "text", "late")));

        assertThat(status).isEqualTo(TaskStatus.CANCELLED);
        assertThat(messages.findAfter(task.id(), -1, 100)).isEmpty();
    }

    @Test
    void messagesForATaskThatIsNotClaimedAreRefused() {
        AgentTask task = queueTask();

        assertThatThrownBy(() -> service.appendMessages(ada, task.id().value(), List.of(line(0, "text", "x"))))
                .isInstanceOf(ConflictException.class);
    }

    // ---- status ------------------------------------------------------------------------------------------

    @Test
    void theDaemonLearnsAboutACancellationFromTheStatus() {
        AgentTask task = running();

        assertThat(service.status(ada, task.id().value())).isEqualTo(TaskStatus.RUNNING);
        task.cancel(clock.instant());
        assertThat(service.status(ada, task.id().value())).isEqualTo(TaskStatus.CANCELLED);
    }

    // ---- complete and fail -------------------------------------------------------------------------------

    @Test
    void completingKeepsTheResultAndAnnouncesIt() {
        AgentTask task = running();

        TaskStatus status = service.complete(
                ada, task.id().value(), new CompletionInput("All done.", "session-9", "/work/9", 1200L, 340L, new BigDecimal("0.05")));

        assertThat(status).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.output()).isEqualTo("All done.");
        assertThat(task.sessionId()).isEqualTo("session-9");
        assertThat(task.workDir()).isEqualTo("/work/9");
        assertThat(task.usage().inputTokens()).isEqualTo(1200);
        assertThat(task.usage().outputTokens()).isEqualTo(340);
        assertThat(task.usage().costUsd()).isEqualByComparingTo("0.05");
        assertThat(published.of(AgentTaskCompleted.class)).singleElement().satisfies(event -> {
            assertThat(event.output()).isEqualTo("All done.");
            assertThat(event.issueId()).isEqualTo(issue.id());
            assertThat(event.agentId()).isEqualTo(builder.id().value());
        });
    }

    @Test
    void completingTwiceIsHarmlessAndAnnouncesOnce() {
        AgentTask task = running();
        CompletionInput input = new CompletionInput("Done", null, null, null, null, null);
        service.complete(ada, task.id().value(), input);

        assertThat(service.complete(ada, task.id().value(), new CompletionInput("Other", null, null, null, null, null)))
                .isEqualTo(TaskStatus.COMPLETED);

        assertThat(task.output()).isEqualTo("Done");
        assertThat(published.of(AgentTaskCompleted.class)).hasSize(1);
    }

    @Test
    void completingATaskThatWasCancelledIsIgnored() {
        AgentTask task = running();
        task.cancel(clock.instant());

        TaskStatus status = service.complete(ada, task.id().value(), new CompletionInput("Late", null, null, null, null, null));

        assertThat(status).isEqualTo(TaskStatus.CANCELLED);
        assertThat(task.output()).isNull();
        assertThat(published.of(AgentTaskCompleted.class)).isEmpty();
    }

    @Test
    void aTaskThatNeverStartedCannotBeCompleted() {
        AgentTask task = claimed();

        assertThatThrownBy(() -> service.complete(ada, task.id().value(), new CompletionInput("x", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void negativeUsageIsRefused() {
        AgentTask task = running();

        assertThatThrownBy(() -> service.complete(ada, task.id().value(), new CompletionInput("x", null, null, -5L, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThat(task.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void failingKeepsTheReasonAndAnnouncesIt() {
        AgentTask task = running();

        TaskStatus status = service.fail(ada, task.id().value(), new FailureInput("The tool crashed", "timeout", "s1", "/w"));

        assertThat(status).isEqualTo(TaskStatus.FAILED);
        assertThat(task.error()).isEqualTo("The tool crashed");
        assertThat(task.failureReason()).isEqualTo("timeout");
        assertThat(published.of(AgentTaskFailed.class)).singleElement().satisfies(event -> {
            assertThat(event.failureReason()).isEqualTo("timeout");
            assertThat(event.error()).isEqualTo("The tool crashed");
            assertThat(event.agentId()).isEqualTo(builder.id().value());
        });
    }

    @Test
    void aTaskCanFailRightAfterTheClaim() {
        AgentTask task = claimed();

        assertThat(service.fail(ada, task.id().value(), new FailureInput("No folder", null, null, null))).isEqualTo(TaskStatus.FAILED);
        assertThat(task.failureReason()).isEqualTo("agent_error");
    }

    @Test
    void aFailureWithoutAnExplanationGetsADefaultOne() {
        AgentTask task = running();

        service.fail(ada, task.id().value(), new FailureInput("  ", null, null, null));

        assertThat(task.error()).isEqualTo(TaskExecutionService.NO_EXPLANATION);
    }

    @Test
    void failingTwiceIsHarmlessAndAnnouncesOnce() {
        AgentTask task = running();
        service.fail(ada, task.id().value(), new FailureInput("First", "timeout", null, null));

        service.fail(ada, task.id().value(), new FailureInput("Second", "agent_error", null, null));

        assertThat(task.error()).isEqualTo("First");
        assertThat(published.of(AgentTaskFailed.class)).hasSize(1);
    }

    @Test
    void failingACancelledTaskIsIgnored() {
        AgentTask task = running();
        task.cancel(clock.instant());

        assertThat(service.fail(ada, task.id().value(), new FailureInput("killed", null, null, null))).isEqualTo(TaskStatus.CANCELLED);

        assertThat(published.of(AgentTaskFailed.class)).isEmpty();
    }

    @Test
    void failingACompletedTaskIsRefused() {
        AgentTask task = running();
        service.complete(ada, task.id().value(), new CompletionInput("Done", null, null, null, null, null));

        assertThatThrownBy(() -> service.fail(ada, task.id().value(), new FailureInput("late", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- whose task is it --------------------------------------------------------------------------------

    @Test
    void everyCallOnSomeoneElsesOrAnUnknownTaskIsNotFoundAndChangesNothing() {
        AgentTask task = running();
        UUID id = task.id().value();
        List<MessageInput> batch = List.of(line(0, "text", "x"));

        assertThatThrownBy(() -> service.start(bob, id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.appendMessages(bob, id, batch)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.status(bob, id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.complete(bob, id, new CompletionInput("x", null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.fail(bob, id, new FailureInput("x", null, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.status(ada, UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        assertThat(task.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(messages.findAfter(task.id(), -1, 10)).isEmpty();
    }
}
