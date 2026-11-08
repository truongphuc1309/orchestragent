package com.orchestragent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.agent.application.Fakes.InMemoryAgents;
import com.orchestragent.agent.application.Fakes.InMemoryRuntimes;
import com.orchestragent.agent.application.Fakes.MutableClock;
import com.orchestragent.agent.application.TaskFakes.FakeIssues;
import com.orchestragent.agent.application.TaskFakes.InMemoryTasks;
import com.orchestragent.agent.application.TaskFakes.Published;
import com.orchestragent.agent.contract.AgentTaskChanged;
import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskTrigger;
import com.orchestragent.issue.contract.ActorType;
import com.orchestragent.issue.contract.CommentAdded;
import com.orchestragent.issue.contract.IssueAssigneeChanged;
import com.orchestragent.issue.contract.IssueDeleted;
import com.orchestragent.issue.contract.IssueDetails;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules that decide when an agent gets work, one test per row of the table in PLAN.md. */
class TaskPlannerTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryRuntimes runtimes = new InMemoryRuntimes();
    private final InMemoryAgents agents = new InMemoryAgents();
    private final InMemoryTasks tasks = new InMemoryTasks(TaskFakes.statusOf(runtimes));
    private final FakeIssues issues = new FakeIssues();
    private final Published published = new Published();
    private final TaskPlanner planner = new TaskPlanner(tasks, agents, issues, new TaskEvents(published, clock), clock);

    private final UUID workspace = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();
    private final Runtime runtime = TaskFakes.runtime(runtimes, workspace, owner, "d1", clock.instant());
    private final Agent builder = agent("Builder");
    private final Agent reviewer = agent("Reviewer");

    private Agent agent(String name) {
        Agent agent = Agent.create(workspace, name, "Do it.", runtime.id(), null, owner, clock.instant());
        agents.save(agent);
        return agent;
    }

    private IssueDetails issueFor(Agent agent) {
        return issues.add(workspace, 1, ActorType.AGENT, agent.id().value(), "todo");
    }

    private IssueAssigneeChanged assigned(IssueDetails issue, ActorType previousType, UUID previous, ActorType newType, UUID next) {
        return new IssueAssigneeChanged(
                UUID.randomUUID(), clock.instant(), workspace, issue.id(), previousType, previous, newType, next, ActorType.MEMBER, person);
    }

    private IssueAssigneeChanged assignedTo(IssueDetails issue, Agent agent) {
        return assigned(issue, null, null, ActorType.AGENT, agent.id().value());
    }

    private CommentAdded comment(IssueDetails issue, ActorType author, boolean changesRequested) {
        return new CommentAdded(
                UUID.randomUUID(), clock.instant(), workspace, issue.id(), UUID.randomUUID(), author,
                author == ActorType.SYSTEM ? null : person, changesRequested);
    }

    private List<AgentTask> tasksOf(IssueDetails issue) {
        return tasks.findByIssue(workspace, issue.id(), 100);
    }

    private AgentTask only(IssueDetails issue) {
        assertThat(tasksOf(issue)).hasSize(1);
        return tasksOf(issue).getFirst();
    }

    // ---- an issue is assigned to an agent ----------------------------------------------------------------

    @Test
    void assigningAnIssueToAnAgentQueuesATaskOnItsRuntime() {
        IssueDetails issue = issueFor(builder);
        IssueAssigneeChanged event = assignedTo(issue, builder);

        planner.assigneeChanged(event);

        AgentTask task = only(issue);
        assertThat(task.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.trigger()).isEqualTo(TaskTrigger.ASSIGNED);
        assertThat(task.agentId()).isEqualTo(builder.id());
        assertThat(task.runtimeId()).isEqualTo(runtime.id());
        assertThat(task.attempt()).isEqualTo(1);
        assertThat(task.workspaceId()).isEqualTo(workspace);
        assertThat(task.sourceEventId()).isEqualTo(event.eventId());
        assertThat(task.triggerCommentId()).isNull();
    }

    @Test
    void queueingATaskAnnouncesIt() {
        IssueDetails issue = issueFor(builder);

        planner.assigneeChanged(assignedTo(issue, builder));

        assertThat(published.of(AgentTaskChanged.class)).singleElement().satisfies(event -> {
            assertThat(event.status()).isEqualTo("queued");
            assertThat(event.issueId()).isEqualTo(issue.id());
            assertThat(event.taskId()).isEqualTo(only(issue).id().value());
        });
    }

    @Test
    void handlingTheSameEventTwiceMakesOneTaskEvenAfterTheFirstWasClaimed() {
        IssueDetails issue = issueFor(builder);
        IssueAssigneeChanged event = assignedTo(issue, builder);
        planner.assigneeChanged(event);
        AgentTask first = only(issue);
        first.dispatch(clock.instant());
        first.start(clock.instant());

        planner.assigneeChanged(event);

        assertThat(tasksOf(issue)).containsExactly(first);
        assertThat(first.status()).as("the redelivery must not cancel the running task").isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void noTaskForADoneOrCancelledIssue() {
        for (String closed : new String[] {"done", "cancelled"}) {
            IssueDetails issue = issues.add(workspace, 2, ActorType.AGENT, builder.id().value(), closed);

            planner.assigneeChanged(assignedTo(issue, builder));

            assertThat(tasksOf(issue)).as(closed).isEmpty();
        }
    }

    @Test
    void everyOtherOpenStatusGetsATask() {
        for (String open : new String[] {"backlog", "todo", "in_progress", "in_review", "blocked"}) {
            IssueDetails issue = issues.add(workspace, 3, ActorType.AGENT, builder.id().value(), open);

            planner.assigneeChanged(assignedTo(issue, builder));

            assertThat(tasksOf(issue)).as(open).hasSize(1);
        }
    }

    @Test
    void noTaskForAnArchivedAgentAnUnknownAgentOrAnAgentOfAnotherWorkspace() {
        Agent archived = agent("Retired");
        archived.archive(clock.instant());
        UUID ghost = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        Agent elsewhere = Agent.create(foreign, "Elsewhere", "", runtime.id(), null, owner, clock.instant());
        agents.save(elsewhere);

        for (UUID agentId : List.of(archived.id().value(), ghost, elsewhere.id().value())) {
            IssueDetails issue = issues.add(workspace, 4, ActorType.AGENT, agentId, "todo");
            planner.assigneeChanged(assigned(issue, null, null, ActorType.AGENT, agentId));
            assertThat(tasksOf(issue)).isEmpty();
        }
    }

    @Test
    void noTaskWhenTheIssueIsNotAssignedToThatAgentAnyMore() {
        IssueDetails issue = issueFor(builder);
        IssueAssigneeChanged old = assignedTo(issue, builder);
        issues.assign(issue, ActorType.MEMBER, person);

        planner.assigneeChanged(old);

        assertThat(tasksOf(issue)).isEmpty();
    }

    @Test
    void noTaskWhenTheIssueWasDeletedMeanwhile() {
        IssueDetails issue = issueFor(builder);
        IssueAssigneeChanged event = assignedTo(issue, builder);
        issues.byId.remove(issue.id());

        planner.assigneeChanged(event);

        assertThat(tasks.stored).isEmpty();
    }

    @Test
    void assigningToAMemberOrToNobodyQueuesNothing() {
        IssueDetails issue = issues.add(workspace, 5, ActorType.MEMBER, person, "todo");

        planner.assigneeChanged(assigned(issue, null, null, ActorType.MEMBER, person));
        planner.assigneeChanged(assigned(issue, ActorType.MEMBER, person, null, null));

        assertThat(tasksOf(issue)).isEmpty();
    }

    @Test
    void assigningWhileATaskIsAlreadyWaitingDoesNotQueueAnother() {
        IssueDetails issue = issueFor(builder);
        planner.assigneeChanged(assignedTo(issue, builder));

        planner.assigneeChanged(assignedTo(issue, builder));

        assertThat(tasksOf(issue)).hasSize(1);
    }

    // ---- the issue goes to someone else, or to nobody ----------------------------------------------------

    @Test
    void unassigningAnAgentCancelsItsQueuedDispatchedAndRunningTasks() {
        IssueDetails issue = issueFor(builder);
        planner.assigneeChanged(assignedTo(issue, builder));
        AgentTask running = only(issue);
        running.dispatch(clock.instant());
        running.start(clock.instant());
        AgentTask dispatched = AgentTask.queue(workspace, issue.id(), builder.id(), runtime.id(), TaskTrigger.COMMENT, null, UUID.randomUUID(), 1, clock.instant());
        dispatched.dispatch(clock.instant());
        tasks.save(dispatched);
        AgentTask queued = AgentTask.queue(workspace, issue.id(), builder.id(), runtime.id(), TaskTrigger.COMMENT, null, UUID.randomUUID(), 1, clock.instant());
        tasks.save(queued);
        issues.assign(issue, null, null);
        published.clear();

        planner.assigneeChanged(assigned(issue, ActorType.AGENT, builder.id().value(), null, null));

        assertThat(List.of(running, dispatched, queued)).allSatisfy(task -> assertThat(task.status()).isEqualTo(TaskStatus.CANCELLED));
        assertThat(published.of(AgentTaskChanged.class)).hasSize(3).allSatisfy(event -> assertThat(event.status()).isEqualTo("cancelled"));
    }

    @Test
    void unassigningLeavesFinishedTasksAlone() {
        IssueDetails issue = issueFor(builder);
        planner.assigneeChanged(assignedTo(issue, builder));
        AgentTask done = only(issue);
        done.dispatch(clock.instant());
        done.start(clock.instant());
        done.complete("Finished", null, null, null, clock.instant());
        issues.assign(issue, null, null);

        planner.assigneeChanged(assigned(issue, ActorType.AGENT, builder.id().value(), null, null));

        assertThat(done.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void reassigningToAnotherAgentCancelsTheOldOnesTasksAndQueuesANewOne() {
        IssueDetails issue = issueFor(builder);
        planner.assigneeChanged(assignedTo(issue, builder));
        AgentTask oldTask = only(issue);
        oldTask.dispatch(clock.instant());
        oldTask.start(clock.instant());
        issues.assign(issue, ActorType.AGENT, reviewer.id().value());

        planner.assigneeChanged(assigned(issue, ActorType.AGENT, builder.id().value(), ActorType.AGENT, reviewer.id().value()));

        assertThat(oldTask.status()).isEqualTo(TaskStatus.CANCELLED);
        AgentTask fresh = tasksOf(issue).stream().filter(AgentTask::isActive).findFirst().orElseThrow();
        assertThat(fresh.agentId()).isEqualTo(reviewer.id());
        assertThat(fresh.trigger()).isEqualTo(TaskTrigger.ASSIGNED);
    }

    @Test
    void reassigningToAMemberCancelsTheAgentsTasksAndQueuesNothing() {
        IssueDetails issue = issueFor(builder);
        planner.assigneeChanged(assignedTo(issue, builder));
        issues.assign(issue, ActorType.MEMBER, person);

        planner.assigneeChanged(assigned(issue, ActorType.AGENT, builder.id().value(), ActorType.MEMBER, person));

        assertThat(only(issue).status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void anotherIssuesTasksAreNeverTouched() {
        IssueDetails issue = issueFor(builder);
        IssueDetails other = issues.add(workspace, 9, ActorType.AGENT, builder.id().value(), "todo");
        planner.assigneeChanged(assignedTo(other, builder));
        issues.assign(issue, null, null);

        planner.assigneeChanged(assigned(issue, ActorType.AGENT, builder.id().value(), null, null));

        assertThat(only(other).status()).isEqualTo(TaskStatus.QUEUED);
    }

    // ---- comments ----------------------------------------------------------------------------------------

    @Test
    void aPersonsCommentOnAnIssueOfAnAgentQueuesATask() {
        IssueDetails issue = issueFor(builder);
        CommentAdded event = comment(issue, ActorType.MEMBER, false);

        planner.commentAdded(event);

        AgentTask task = only(issue);
        assertThat(task.trigger()).isEqualTo(TaskTrigger.COMMENT);
        assertThat(task.triggerCommentId()).isEqualTo(event.commentId());
        assertThat(task.sourceEventId()).isEqualTo(event.eventId());
        assertThat(task.agentId()).isEqualTo(builder.id());
        assertThat(task.runtimeId()).isEqualTo(runtime.id());
    }

    @Test
    void aCommentOfAnAgentOrOfTheSystemNeverStartsWork() {
        IssueDetails issue = issueFor(builder);

        planner.commentAdded(comment(issue, ActorType.AGENT, false));
        planner.commentAdded(comment(issue, ActorType.SYSTEM, false));

        assertThat(tasks.stored).isEmpty();
    }

    @Test
    void aCommentWhileATaskIsWaitingQueuesNothingBecauseThatTaskWillReadIt() {
        IssueDetails issue = issueFor(builder);
        planner.commentAdded(comment(issue, ActorType.MEMBER, false));

        planner.commentAdded(comment(issue, ActorType.MEMBER, false));
        planner.commentAdded(comment(issue, ActorType.MEMBER, true));

        assertThat(tasksOf(issue)).hasSize(1);
    }

    @Test
    void aCommentWhileATaskIsRunningQueuesTheNextOne() {
        IssueDetails issue = issueFor(builder);
        planner.commentAdded(comment(issue, ActorType.MEMBER, false));
        AgentTask running = only(issue);
        running.dispatch(clock.instant());
        running.start(clock.instant());

        planner.commentAdded(comment(issue, ActorType.MEMBER, false));

        assertThat(tasksOf(issue)).hasSize(2);
        assertThat(tasksOf(issue).stream().filter(task -> task.status() == TaskStatus.QUEUED)).hasSize(1);
        assertThat(running.status()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void aCommentAfterTheTaskFinishedQueuesANewOne() {
        IssueDetails issue = issueFor(builder);
        planner.commentAdded(comment(issue, ActorType.MEMBER, false));
        AgentTask first = only(issue);
        first.dispatch(clock.instant());
        first.start(clock.instant());
        first.complete("Done", "s1", "/w", null, clock.instant());

        planner.commentAdded(comment(issue, ActorType.MEMBER, false));

        assertThat(tasksOf(issue)).hasSize(2);
    }

    @Test
    void handlingTheSameCommentEventTwiceMakesOneTask() {
        IssueDetails issue = issueFor(builder);
        CommentAdded event = comment(issue, ActorType.MEMBER, false);
        planner.commentAdded(event);
        AgentTask first = only(issue);
        first.dispatch(clock.instant());
        first.start(clock.instant());
        first.complete("Done", null, null, null, clock.instant());

        planner.commentAdded(event);

        assertThat(tasksOf(issue)).hasSize(1);
    }

    @Test
    void aCommentOnAnIssueNotAssignedToAnAgentQueuesNothing() {
        IssueDetails ofMember = issues.add(workspace, 6, ActorType.MEMBER, person, "todo");
        IssueDetails ofNobody = issues.add(workspace, 7, null, null, "todo");

        planner.commentAdded(comment(ofMember, ActorType.MEMBER, false));
        planner.commentAdded(comment(ofNobody, ActorType.MEMBER, false));

        assertThat(tasks.stored).isEmpty();
    }

    @Test
    void aCommentOnAnIssueOfAnArchivedAgentOrAMissingIssueQueuesNothing() {
        Agent archived = agent("Retired");
        archived.archive(clock.instant());
        IssueDetails issue = issueFor(archived);
        IssueDetails gone = issueFor(builder);
        issues.byId.remove(gone.id());

        planner.commentAdded(comment(issue, ActorType.MEMBER, false));
        planner.commentAdded(comment(gone, ActorType.MEMBER, false));

        assertThat(tasks.stored).isEmpty();
    }

    @Test
    void aCommentOnADoneIssueStillWakesTheAgent() {
        IssueDetails issue = issues.add(workspace, 8, ActorType.AGENT, builder.id().value(), "done");

        planner.commentAdded(comment(issue, ActorType.MEMBER, false));

        assertThat(only(issue).trigger()).isEqualTo(TaskTrigger.COMMENT);
    }

    // ---- a reviewer sends the issue back -----------------------------------------------------------------

    @Test
    void sendingAnIssueBackWithACommentQueuesAChangesRequestedTask() {
        IssueDetails issue = issues.add(workspace, 10, ActorType.AGENT, builder.id().value(), "todo");
        CommentAdded event = comment(issue, ActorType.MEMBER, true);

        planner.commentAdded(event);

        AgentTask task = only(issue);
        assertThat(task.trigger()).isEqualTo(TaskTrigger.CHANGES_REQUESTED);
        assertThat(task.triggerCommentId()).isEqualTo(event.commentId());
    }

    // ---- an issue is deleted -----------------------------------------------------------------------------

    @Test
    void deletingAnIssueCancelsItsActiveTasksOnly() {
        IssueDetails issue = issueFor(builder);
        planner.commentAdded(comment(issue, ActorType.MEMBER, false));
        AgentTask finished = only(issue);
        finished.dispatch(clock.instant());
        finished.start(clock.instant());
        finished.complete("Done", null, null, null, clock.instant());
        clock.advance(Duration.ofSeconds(1));
        planner.commentAdded(comment(issue, ActorType.MEMBER, false));
        AgentTask waiting = tasksOf(issue).stream().filter(AgentTask::isActive).findFirst().orElseThrow();
        published.clear();

        planner.issueDeleted(new IssueDeleted(UUID.randomUUID(), clock.instant(), workspace, issue.id(), "WEB-1"));

        assertThat(waiting.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(finished.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(published.of(AgentTaskChanged.class)).singleElement().satisfies(event -> assertThat(event.status()).isEqualTo("cancelled"));
    }
}
