package com.orchestragent.agent.application;

import com.orchestragent.agent.application.TaskViews.AgentBrief;
import com.orchestragent.agent.application.TaskViews.ClaimedTask;
import com.orchestragent.agent.application.TaskViews.CommentBrief;
import com.orchestragent.agent.application.TaskViews.IssueBrief;
import com.orchestragent.agent.domain.Agent;
import com.orchestragent.agent.domain.AgentRepository;
import com.orchestragent.agent.domain.AgentTask;
import com.orchestragent.agent.domain.MessageType;
import com.orchestragent.agent.domain.Runtime;
import com.orchestragent.agent.domain.RuntimeId;
import com.orchestragent.agent.domain.RuntimeRepository;
import com.orchestragent.agent.domain.TaskId;
import com.orchestragent.agent.domain.TaskMessage;
import com.orchestragent.agent.domain.TaskMessageRepository;
import com.orchestragent.agent.domain.TaskRepository;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskUsage;
import com.orchestragent.issue.contract.CommentDetails;
import com.orchestragent.issue.contract.IssueDetails;
import com.orchestragent.issue.contract.IssueDirectory;
import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.ValidationException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The task side of the daemon API: claim work, report progress, finish. Every method acts for the owner of the
 * API token. A daemon only ever sees and changes tasks of runtimes its owner owns, and a task that is not the
 * caller's is "not found", exactly like one that does not exist.
 *
 * <p>The calls a daemon repeats when its network drops are safe to repeat (see {@link AgentTask}). After a task
 * was cancelled, the daemon's progress reports are accepted and ignored, so it can wind down quietly; it learns
 * about the cancellation from {@link #status}.
 */
@Service
public class TaskExecutionService {

    /** The most tasks one claim call hands out. */
    static final int MAX_CLAIM = 10;

    /** The most runtimes one claim call may ask for. */
    static final int MAX_RUNTIMES = 50;

    /** The most log lines in one batch. */
    static final int MAX_BATCH = 200;

    static final String NO_EXPLANATION = "The agent reported no explanation.";

    private final TaskRepository tasks;
    private final TaskMessageRepository messages;
    private final AgentRepository agents;
    private final RuntimeRepository runtimes;
    private final IssueDirectory issues;
    private final WorkspaceAccess access;
    private final TaskEvents events;
    private final Clock clock;

    TaskExecutionService(
            TaskRepository tasks,
            TaskMessageRepository messages,
            AgentRepository agents,
            RuntimeRepository runtimes,
            IssueDirectory issues,
            WorkspaceAccess access,
            TaskEvents events,
            Clock clock) {
        this.tasks = tasks;
        this.messages = messages;
        this.agents = agents;
        this.runtimes = runtimes;
        this.issues = issues;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /** One line of a task's log as the daemon sends it. */
    public record MessageInput(long seq, String type, String content) {
    }

    /** What the daemon reports when a task completes. All of it is optional except that the task was running. */
    public record CompletionInput(
            String output, String sessionId, String workDir, Long inputTokens, Long outputTokens, BigDecimal costUsd) {
    }

    /** What the daemon reports when a task fails. */
    public record FailureInput(String error, String failureReason, String sessionId, String workDir) {
    }

    // ---- claim -------------------------------------------------------------------------------------------

    /**
     * Hands out waiting tasks of the given runtimes, which must belong to the user. Runtimes of workspaces the
     * user has left are skipped, so a former member's daemon gets no more work (and no more issue content).
     */
    @Transactional
    public List<ClaimedTask> claim(UUID userId, Collection<UUID> runtimeIds, int maxTasks) {
        if (runtimeIds.size() > MAX_RUNTIMES) {
            throw new ValidationException("daemon.too_many_runtimes", "Ask for at most " + MAX_RUNTIMES + " runtimes");
        }
        Map<RuntimeId, Runtime> owned = runtimes.findOwnedBy(userId, runtimeIds.stream().map(RuntimeId::new).toList())
                .stream()
                .filter(runtime -> access.isMember(runtime.workspaceId(), userId))
                .collect(Collectors.toMap(Runtime::id, Function.identity()));
        if (owned.isEmpty()) {
            return List.of();
        }

        Instant now = clock.instant();
        List<ClaimedTask> result = new java.util.ArrayList<>();
        for (AgentTask task : tasks.claim(owned.keySet(), Math.clamp(maxTasks, 1, MAX_CLAIM), now)) {
            Optional<ClaimedTask> brief = brief(task, owned.get(task.runtimeId()));
            if (brief.isPresent()) {
                events.changed(task);
                result.add(brief.get());
            } else {
                // The issue or the agent vanished between queueing and claiming: there is nothing to run.
                task.cancel(now);
                tasks.save(task);
                events.changed(task);
            }
        }
        return result;
    }

    private Optional<ClaimedTask> brief(AgentTask task, Runtime runtime) {
        Optional<Agent> agent = agents.find(task.workspaceId(), task.agentId()).filter(a -> !a.isArchived());
        Optional<IssueDetails> issue = issues.find(task.workspaceId(), task.issueId());
        if (agent.isEmpty() || issue.isEmpty()) {
            return Optional.empty();
        }
        List<CommentBrief> comments = issues.listComments(task.workspaceId(), task.issueId()).stream()
                .map(TaskExecutionService::brief)
                .toList();
        // Resume the AI tool's earlier session on this issue, in the same folder, if this agent has one.
        Optional<AgentTask> prior = tasks.findLastResumable(task.issueId(), task.agentId())
                .filter(earlier -> !earlier.id().equals(task.id()));
        return Optional.of(new ClaimedTask(
                task.id().value(),
                task.workspaceId(),
                task.runtimeId().value(),
                runtime.provider(),
                task.trigger(),
                task.attempt(),
                new AgentBrief(agent.get().id().value(), agent.get().name(), agent.get().instructions(), agent.get().model()),
                new IssueBrief(
                        issue.get().id(), issue.get().number(), issue.get().title(), issue.get().description()),
                comments,
                prior.map(AgentTask::sessionId).orElse(null),
                prior.map(AgentTask::workDir).orElse(null)));
    }

    private static CommentBrief brief(CommentDetails comment) {
        return new CommentBrief(
                comment.authorType().name().toLowerCase(Locale.ROOT), comment.authorName(), comment.body(), comment.createdAt());
    }

    // ---- progress ----------------------------------------------------------------------------------------

    /**
     * The daemon starts a task it claimed. A task that was cancelled meanwhile stays cancelled; the answer
     * tells the daemon, which then does not run it.
     */
    @Transactional
    public TaskStatus start(UUID userId, UUID taskId) {
        AgentTask task = owned(userId, taskId);
        if (task.status() == TaskStatus.CANCELLED) {
            return task.status();
        }
        if (task.start(clock.instant())) {
            tasks.save(task);
            events.started(task);
        }
        return task.status();
    }

    /**
     * Stores a batch of log lines. Lines already stored (same number) are skipped, so resending is safe.
     * Lines for a task that already finished are dropped.
     *
     * @return the status of the task
     */
    @Transactional
    public TaskStatus appendMessages(UUID userId, UUID taskId, List<MessageInput> batch) {
        if (batch.size() > MAX_BATCH) {
            throw new ValidationException("task.batch_too_large", "Send at most " + MAX_BATCH + " messages at once");
        }
        AgentTask task = owned(userId, taskId);
        if (task.status() == TaskStatus.QUEUED) {
            throw new ConflictException("task.lease_lost", "The task is not claimed by this daemon any more");
        }
        if (task.status().isFinished()) {
            return task.status();
        }

        Instant now = clock.instant();
        Set<Long> seen = new HashSet<>();
        List<TaskMessage> parsed = new java.util.ArrayList<>();
        for (MessageInput input : batch) {
            if (seen.add(input.seq())) {
                parsed.add(TaskMessage.received(input.seq(), MessageType.fromCode(input.type()), input.content(), now));
            }
        }
        if (messages.append(task.id(), parsed) > 0) {
            events.messages(task, messages.lastSeq(task.id()).orElse(-1));
        }
        return task.status();
    }

    /** The task's status, which the daemon polls to learn that someone cancelled it. */
    @Transactional(readOnly = true)
    public TaskStatus status(UUID userId, UUID taskId) {
        return owned(userId, taskId).status();
    }

    /** The task succeeded. Repeating the call is harmless; completing a cancelled task is ignored. */
    @Transactional
    public TaskStatus complete(UUID userId, UUID taskId, CompletionInput input) {
        AgentTask task = owned(userId, taskId);
        if (task.status() == TaskStatus.CANCELLED) {
            return task.status();
        }
        TaskUsage usage = new TaskUsage(input.inputTokens(), input.outputTokens(), input.costUsd());
        if (task.complete(input.output(), input.sessionId(), input.workDir(), usage, clock.instant())) {
            tasks.save(task);
            events.completed(task);
        }
        return task.status();
    }

    /** The task failed. Repeating the call is harmless; failing a cancelled task is ignored. */
    @Transactional
    public TaskStatus fail(UUID userId, UUID taskId, FailureInput input) {
        AgentTask task = owned(userId, taskId);
        if (task.status() == TaskStatus.CANCELLED) {
            return task.status();
        }
        String error = input.error() == null || input.error().isBlank() ? NO_EXPLANATION : input.error();
        if (task.fail(error, input.failureReason(), input.sessionId(), input.workDir(), clock.instant())) {
            tasks.save(task);
            events.failed(task);
        }
        return task.status();
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    /** The task, if it belongs to a runtime of this user; otherwise "not found". */
    private AgentTask owned(UUID userId, UUID taskId) {
        AgentTask task = tasks.findById(new TaskId(taskId)).orElseThrow(TaskExecutionService::notFound);
        if (runtimes.findOwnedBy(userId, List.of(task.runtimeId())).isEmpty()) {
            throw notFound();
        }
        return task;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("task.not_found", "Task not found");
    }
}
