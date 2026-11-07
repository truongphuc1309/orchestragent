package com.orchestragent.daemon.cli.runner;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.provider.CancellationToken;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Asks the server for the status of a running task every {@code interval} (five seconds by default) and cancels the
 * run when the task is no longer running there: someone cancelled it ({@link TaskRunner#CANCELLED}), or the server
 * finished it without us or does not know it any more ({@link TaskRunner#LOST}). When the server cannot be reached
 * the run goes on; the next poll tries again.
 */
public final class CancellationWatcher implements AutoCloseable {

    private static final Logger LOG = System.getLogger(CancellationWatcher.class.getName());

    private final UUID taskId;
    private final Function<UUID, String> status;
    private final CancellationToken token;
    private ScheduledFuture<?> schedule;
    private String lastError;

    /** @param status the server's {@code status} call */
    public CancellationWatcher(UUID taskId, Function<UUID, String> status, CancellationToken token) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.status = Objects.requireNonNull(status, "status");
        this.token = Objects.requireNonNull(token, "token");
    }

    public synchronized void start(ScheduledExecutorService scheduler, Duration interval) {
        if (schedule == null) {
            long millis = Math.max(1, interval.toMillis());
            schedule = scheduler.scheduleWithFixedDelay(this::check, millis, millis, TimeUnit.MILLISECONDS);
        }
    }

    /** Polls once. */
    void check() {
        if (token.isCancelled()) {
            return;
        }
        try {
            String current = status.apply(taskId);
            lastError = null;
            if (TaskRunner.CANCELLED.equals(current)) {
                token.cancel(TaskRunner.CANCELLED);
            } else if (!"running".equals(current)) {
                // Back in the queue (lease lost) or finished by the server (runtime offline): not ours to finish.
                token.cancel(TaskRunner.LOST);
            }
        } catch (DaemonApiException e) {
            if (e.isNotFound()) {
                token.cancel(TaskRunner.LOST);
            } else if (!Objects.equals(lastError, e.getMessage())) {
                LOG.log(Level.WARNING, "Task {0}: cannot check the status: {1}", TaskRunner.shortId(taskId), e.getMessage());
                lastError = e.getMessage();
            }
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Task " + taskId + ": checking the status failed", e);
        }
    }

    @Override
    public synchronized void close() {
        if (schedule != null) {
            schedule.cancel(false);
        }
    }
}
