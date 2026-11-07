package com.orchestragent.agent.domain;

import com.orchestragent.shared.error.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One run of an agent on an issue. The task is the unit a daemon claims and works on, and the state machine
 * here is the only place that says which changes are allowed:
 *
 * <pre>
 *   QUEUED -> DISPATCHED -> RUNNING -> COMPLETED
 *                  |            |  \-> FAILED
 *                  |            \----> CANCELLED (from any active state)
 *                  \-> QUEUED  (the daemon did not start in time: the lease ran out)
 * </pre>
 *
 * Calls a daemon may repeat (it retries with backoff) are idempotent: repeating one that already happened
 * changes nothing and returns {@code false}.
 */
public final class AgentTask {

    /** How long a daemon has to start a task it claimed before the task goes back to the queue. */
    public static final Duration LEASE = Duration.ofSeconds(60);

    public static final int MAX_OUTPUT_LENGTH = 100_000;
    public static final int MAX_ERROR_LENGTH = 10_000;

    /** The failure reason used when the daemon does not give one. */
    public static final String DEFAULT_FAILURE_REASON = "agent_error";

    private static final Pattern REASON = Pattern.compile("[a-z0-9_]{1,50}");
    private static final String CUT = "\n… (cut: too long)";

    private final TaskId id;
    private final UUID workspaceId;
    private final UUID issueId;
    private final AgentId agentId;
    private RuntimeId runtimeId;
    private final TaskTrigger trigger;
    private final UUID triggerCommentId;
    private final UUID sourceEventId;
    private final int attempt;
    private final Instant createdAt;
    private TaskStatus status;
    private String sessionId;
    private String workDir;
    private String output;
    private String error;
    private String failureReason;
    private TaskUsage usage;
    private Instant leaseExpiresAt;
    private Instant startedAt;
    private Instant finishedAt;

    public AgentTask(
            TaskId id,
            UUID workspaceId,
            UUID issueId,
            AgentId agentId,
            RuntimeId runtimeId,
            TaskTrigger trigger,
            UUID triggerCommentId,
            UUID sourceEventId,
            int attempt,
            TaskStatus status,
            String sessionId,
            String workDir,
            String output,
            String error,
            String failureReason,
            TaskUsage usage,
            Instant leaseExpiresAt,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.issueId = Objects.requireNonNull(issueId, "issueId");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.triggerCommentId = triggerCommentId;
        this.sourceEventId = sourceEventId;
        this.attempt = attempt;
        this.status = Objects.requireNonNull(status, "status");
        this.sessionId = sessionId;
        this.workDir = workDir;
        this.output = output;
        this.error = error;
        this.failureReason = failureReason;
        this.usage = usage == null ? TaskUsage.NONE : usage;
        this.leaseExpiresAt = leaseExpiresAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /**
     * A new task, waiting in the queue.
     *
     * @param triggerCommentId the comment that caused the task, if any
     * @param sourceEventId the event that caused the task, so handling the same event twice makes one task;
     *        null for tasks a person asked for directly (retry)
     */
    public static AgentTask queue(
            UUID workspaceId,
            UUID issueId,
            AgentId agentId,
            RuntimeId runtimeId,
            TaskTrigger trigger,
            UUID triggerCommentId,
            UUID sourceEventId,
            int attempt,
            Instant now) {
        return new AgentTask(
                TaskId.generate(),
                workspaceId,
                issueId,
                agentId,
                runtimeId,
                trigger,
                triggerCommentId,
                sourceEventId,
                attempt,
                TaskStatus.QUEUED,
                null,
                null,
                null,
                null,
                null,
                TaskUsage.NONE,
                null,
                now,
                null,
                null);
    }

    // ---- state changes -----------------------------------------------------------------------------------

    /** A daemon claimed the task. It must start it before the lease ends. */
    public void dispatch(Instant now) {
        if (status != TaskStatus.QUEUED) {
            throw invalid("Only a queued task can be handed to a daemon");
        }
        status = TaskStatus.DISPATCHED;
        leaseExpiresAt = now.plus(LEASE);
    }

    /** The daemon did not start in time: back to the queue. */
    public void requeue() {
        if (status != TaskStatus.DISPATCHED) {
            throw invalid("Only a dispatched task can go back to the queue");
        }
        status = TaskStatus.QUEUED;
        leaseExpiresAt = null;
    }

    /**
     * The daemon starts the claimed task.
     *
     * @return false when it was already running (the daemon repeated the call)
     */
    public boolean start(Instant now) {
        switch (status) {
            case RUNNING:
                return false;
            case DISPATCHED:
                status = TaskStatus.RUNNING;
                startedAt = now;
                leaseExpiresAt = null;
                return true;
            case QUEUED:
                throw new ConflictException("task.lease_lost", "The task is not claimed by this daemon any more");
            default:
                throw invalid("The task is already " + status.code());
        }
    }

    /**
     * The run succeeded.
     *
     * @return false when the task was already completed (a repeated call)
     */
    public boolean complete(String finalOutput, String newSessionId, String newWorkDir, TaskUsage newUsage, Instant now) {
        if (status == TaskStatus.COMPLETED) {
            return false;
        }
        if (status != TaskStatus.RUNNING) {
            throw invalid("Only a running task can complete, this one is " + status.code());
        }
        status = TaskStatus.COMPLETED;
        output = cut(finalOutput, MAX_OUTPUT_LENGTH);
        sessionId = blankToNull(newSessionId);
        workDir = blankToNull(newWorkDir);
        usage = newUsage == null ? TaskUsage.NONE : newUsage;
        finishedAt = now;
        return true;
    }

    /**
     * The run failed (or could not even start).
     *
     * @return false when the task was already failed (a repeated call)
     */
    public boolean fail(String message, String reason, String newSessionId, String newWorkDir, Instant now) {
        if (status == TaskStatus.FAILED) {
            return false;
        }
        if (status != TaskStatus.DISPATCHED && status != TaskStatus.RUNNING) {
            throw invalid("Only a claimed task can fail, this one is " + status.code());
        }
        status = TaskStatus.FAILED;
        error = cut(message, MAX_ERROR_LENGTH);
        failureReason = validReason(reason);
        sessionId = blankToNull(newSessionId);
        workDir = blankToNull(newWorkDir);
        finishedAt = now;
        leaseExpiresAt = null;
        return true;
    }

    /** Stops the task. Returns false when it had already finished (nothing to cancel). */
    public boolean cancel(Instant now) {
        if (!status.isActive()) {
            return false;
        }
        status = TaskStatus.CANCELLED;
        finishedAt = now;
        leaseExpiresAt = null;
        return true;
    }

    /** A waiting task follows its agent when the agent moves to another runtime. */
    public void moveToRuntime(RuntimeId newRuntime) {
        if (status != TaskStatus.QUEUED) {
            throw invalid("Only a queued task can move to another runtime");
        }
        runtimeId = Objects.requireNonNull(newRuntime, "newRuntime");
    }

    // ---- validation --------------------------------------------------------------------------------------

    /** A short code such as {@code agent_error} or {@code timeout}; anything else becomes the default. */
    public static String validReason(String raw) {
        String reason = raw == null ? "" : raw.trim().toLowerCase();
        return REASON.matcher(reason).matches() ? reason : DEFAULT_FAILURE_REASON;
    }

    private static String cut(String text, int max) {
        String value = text == null ? "" : text;
        return value.length() <= max ? value : value.substring(0, max - CUT.length()) + CUT;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private ConflictException invalid(String message) {
        return new ConflictException("task.invalid_state", message);
    }

    // ---- accessors ---------------------------------------------------------------------------------------

    public TaskId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID issueId() {
        return issueId;
    }

    public AgentId agentId() {
        return agentId;
    }

    public RuntimeId runtimeId() {
        return runtimeId;
    }

    public TaskTrigger trigger() {
        return trigger;
    }

    /** The comment that caused this task, or null. */
    public UUID triggerCommentId() {
        return triggerCommentId;
    }

    /** The event that caused this task, or null when a person asked for it directly. */
    public UUID sourceEventId() {
        return sourceEventId;
    }

    /** 1 for the first run, higher for retries. */
    public int attempt() {
        return attempt;
    }

    public TaskStatus status() {
        return status;
    }

    /** The AI tool's session, to resume it in a later run; null until known. */
    public String sessionId() {
        return sessionId;
    }

    /** The folder the run happened in on the daemon's computer; a resumed session needs the same folder. */
    public String workDir() {
        return workDir;
    }

    public String output() {
        return output;
    }

    public String error() {
        return error;
    }

    public String failureReason() {
        return failureReason;
    }

    public TaskUsage usage() {
        return usage;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    /** Alias that reads better where only the active/finished question matters. */
    public boolean isActive() {
        return status.isActive();
    }

}
