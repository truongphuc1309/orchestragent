package com.orchestragent.daemon.cli.runner;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.cli.client.Retry;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.CompleteRequest;
import com.orchestragent.daemon.protocol.FailRequest;
import com.orchestragent.daemon.protocol.TaskUsage;
import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import com.orchestragent.daemon.provider.Usage;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Runs one claimed task from start to end: prepares its folder and prompt, tells the server it starts, runs the
 * provider while sending the log ({@link MessageBatcher}) and watching for a cancellation
 * ({@link CancellationWatcher}), then reports the result. Reports are retried with backoff, which is safe because
 * the server accepts them more than once.
 *
 * <p>Every log line is sent before the result, because the server drops lines of a finished task.
 */
public final class TaskRunner {

    /** Cancelled on the server by a person (or because the issue changed). Nothing is reported then. */
    public static final String CANCELLED = "cancelled";

    /** The task is no longer this daemon's (lease lost, finished by the server, unknown). Nothing is reported. */
    public static final String LOST = "lost";

    /** The daemon is shutting down and did not wait for the run any longer: reported as {@code daemon_stopped}. */
    public static final String SHUTDOWN = "shutdown";

    private static final Logger LOG = System.getLogger(TaskRunner.class.getName());
    private static final Set<String> FINISHED = Set.of("completed", "failed", "cancelled");

    /** How often to send the log, poll the status, and how long a run may take. */
    public record Timing(Duration flushInterval, Duration statusInterval, Duration taskTimeout) {

        public static Timing defaults() {
            return new Timing(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(30));
        }
    }

    private final DaemonClient client;
    private final Map<String, AgentProvider> providers;
    private final WorkDirs workDirs;
    private final PromptBuilder prompts;
    private final ScheduledExecutorService scheduler;
    private final Timing timing;
    private final Retry retry;

    public TaskRunner(
            DaemonClient client,
            Map<String, AgentProvider> providers,
            WorkDirs workDirs,
            PromptBuilder prompts,
            ScheduledExecutorService scheduler,
            Timing timing,
            Retry retry) {
        this.client = Objects.requireNonNull(client, "client");
        this.providers = Map.copyOf(providers);
        this.workDirs = Objects.requireNonNull(workDirs, "workDirs");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.timing = Objects.requireNonNull(timing, "timing");
        this.retry = Objects.requireNonNull(retry, "retry");
    }

    /** True for the statuses after which a task never runs again. */
    public static boolean isFinished(String status) {
        return status != null && FINISHED.contains(status);
    }

    /**
     * Runs the task and reports how it ended. Never throws: what goes wrong is reported to the server when it can
     * be, and logged.
     *
     * @param token cancelled by the daemon (with {@link #SHUTDOWN}) when it stops; the watcher cancels it too
     */
    public void run(ClaimedTask task, CancellationToken token) {
        String id = shortId(task.taskId());
        LOG.log(Level.INFO, "Task {0}: issue #{1} \"{2}\" for agent {3} ({4}, trigger {5})",
                id, task.issue().number(), task.issue().title(), task.agent().name(), task.provider(), task.trigger());
        try {
            execute(task, token, id);
        } catch (DaemonApiException e) {
            LOG.log(Level.ERROR, "Task {0}: giving up talking to the server: {1}", id, e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Task " + id + ": unexpected error", e);
        }
    }

    private void execute(ClaimedTask task, CancellationToken token, String id) {
        AgentProvider provider = providers.get(task.provider());
        if (provider == null) {
            reportFailure(task, id, "This daemon does not run the provider " + task.provider(), "provider_unavailable", null, null);
            return;
        }

        WorkDirs.WorkDir workDir;
        try {
            workDir = workDirs.prepare(task);
        } catch (IOException | RuntimeException e) {
            reportFailure(task, id, "Cannot prepare the work folder: " + e.getMessage(), "workdir_error", null, null);
            return;
        }
        String prompt = prompts.build(task);

        String started;
        try {
            started = retry.call(() -> client.start(task.taskId()));
        } catch (DaemonApiException e) {
            if (e.isConflict() || e.isNotFound()) {
                LOG.log(Level.WARNING, "Task {0}: not ours any more ({1}), skipping it", id, e.getMessage());
                return;
            }
            throw e;
        }
        if (!"running".equals(started)) {
            LOG.log(Level.INFO, "Task {0}: is {1} on the server, not running it", id, started);
            return;
        }

        // A session can only be resumed in the folder it was made in.
        String resume = workDir.reused() ? task.priorSessionId() : null;
        RunRequest request = new RunRequest(prompt, workDir.path(), task.agent().model(), resume, timing.taskTimeout());
        LOG.log(Level.INFO, "Task {0}: running in {1}{2}", id, workDir.path(), resume == null ? "" : " (resuming " + resume + ")");

        RunResult result;
        MessageBatcher log = new MessageBatcher(task.taskId(), client::messages,
                status -> token.cancel(CANCELLED.equals(status) ? CANCELLED : LOST));
        try (CancellationWatcher watcher = new CancellationWatcher(task.taskId(), client::status, token)) {
            log.start(scheduler, timing.flushInterval());
            watcher.start(scheduler, timing.statusInterval());
            result = runProvider(provider, request, log, token);
        } finally {
            log.close();
        }

        if (token.isCancelled()) {
            switch (token.reason()) {
                case SHUTDOWN -> reportFailure(task, id, "The daemon stopped before the run finished", "daemon_stopped",
                        result.sessionId(), workDir);
                case CANCELLED -> LOG.log(Level.INFO, "Task {0}: cancelled on the server, stopped", id);
                default -> LOG.log(Level.WARNING, "Task {0}: no longer ours on the server, result dropped", id);
            }
            return;
        }
        if (result.isSuccess()) {
            String status = retry.call(() -> client.complete(task.taskId(), new CompleteRequest(
                    result.output(), result.sessionId(), workDir.path().toString(), usage(result.usage()))));
            LOG.log(Level.INFO, "Task {0}: completed ({1} log lines), server says {2}", id, log.count(), status);
        } else {
            reportFailure(task, id, result.error(), result.failureReason(), result.sessionId(), workDir);
        }
    }

    /** Runs the provider; an exception of the provider becomes a failed result. */
    private static RunResult runProvider(
            AgentProvider provider, RunRequest request, MessageBatcher log, CancellationToken token) {
        try {
            RunResult result = provider.run(request, log, token);
            return result == null ? RunResult.failed("The provider returned no result", "provider_error", null, null) : result;
        } catch (RuntimeException e) {
            String message = "The provider " + provider.id() + " crashed: " + e;
            log.onMessage(MessageType.ERROR, message);
            return RunResult.failed(message, "provider_error", null, null);
        }
    }

    private void reportFailure(
            ClaimedTask task, String id, String error, String reason, String sessionId, WorkDirs.WorkDir workDir) {
        String status = retry.call(() -> client.fail(task.taskId(), new FailRequest(
                error, reason, sessionId, workDir == null ? null : workDir.path().toString())));
        LOG.log(Level.WARNING, "Task {0}: failed ({1}: {2}), server says {3}", id, reason, error, status);
    }

    private static TaskUsage usage(Usage usage) {
        return new TaskUsage(usage.inputTokens(), usage.outputTokens(), usage.costUsd());
    }

    /**
     * The last eight characters of a task ID, for logs and folder names. IDs are UUIDv7, whose first characters are
     * a timestamp shared by every task made within a minute or so; the end is random.
     */
    public static String shortId(UUID id) {
        String text = id.toString();
        return text.substring(text.length() - 8);
    }
}
