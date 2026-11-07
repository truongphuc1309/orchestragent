package com.orchestragent.daemon.cli.runner;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.protocol.TaskMessage;
import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.RunListener;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Collects the log lines of a run and sends them to the server in batches, every {@code interval} (one second by
 * default). Lines are numbered from 0 in the order they arrive ({@code seq}). A batch that fails stays and is sent
 * again with the same numbers, which the server accepts without storing twice.
 *
 * <p>When the server's answer says the task has finished (someone cancelled it, say) or it does not know the task
 * any more, {@code onStopped} is told, once, and the remaining lines are dropped. Thread-safe.
 */
public final class MessageBatcher implements RunListener, AutoCloseable {

    /** What sends a batch: the server's {@code messages} call. Returns the task's status. */
    @FunctionalInterface
    public interface Sink {
        String send(UUID taskId, List<TaskMessage> batch);
    }

    /** The most lines in one request (the server's limit). */
    static final int MAX_BATCH = 200;

    /** The most characters of content in one request, to keep requests small. */
    static final int MAX_BATCH_CHARS = 1_000_000;

    /** The longest line kept (the server's limit); longer lines are cut. */
    static final int MAX_CONTENT = 50_000;

    /** Lines kept while the server cannot be reached; beyond that the oldest are dropped. */
    static final int MAX_PENDING = 10_000;

    private static final Logger LOG = System.getLogger(MessageBatcher.class.getName());
    private static final String CUT = "\n… (cut: too long)";

    private final UUID taskId;
    private final Sink sink;
    private final Consumer<String> onStopped;
    private final Object flushLock = new Object();

    private final Deque<TaskMessage> pending = new ArrayDeque<>();
    private long nextSeq;
    private long dropped;
    private boolean stopped;
    private String lastError;
    private ScheduledFuture<?> schedule;

    /**
     * @param onStopped told the task's status (or {@code lost}) when the server says the task is no longer running
     */
    public MessageBatcher(UUID taskId, Sink sink, Consumer<String> onStopped) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.onStopped = Objects.requireNonNull(onStopped, "onStopped");
    }

    /** Starts sending every {@code interval} on {@code scheduler}. */
    public synchronized void start(ScheduledExecutorService scheduler, Duration interval) {
        if (schedule == null) {
            long millis = Math.max(1, interval.toMillis());
            schedule = scheduler.scheduleWithFixedDelay(this::flushQuietly, millis, millis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onMessage(MessageType type, String content) {
        String text = content == null ? "" : content;
        if (text.length() > MAX_CONTENT) {
            text = text.substring(0, MAX_CONTENT - CUT.length()) + CUT;
        }
        synchronized (this) {
            if (stopped) {
                return;
            }
            pending.addLast(new TaskMessage(nextSeq++, type.code(), text));
            if (pending.size() > MAX_PENDING) {
                pending.removeFirst();
                dropped++;
            }
        }
    }

    /**
     * Sends everything collected so far, in as many batches as needed.
     *
     * @return true when nothing is left to send (all sent, or the task stopped)
     * @throws DaemonApiException when the server could not be reached; the lines stay for the next try
     */
    public boolean flush() {
        synchronized (flushLock) {
            while (true) {
                List<TaskMessage> batch = nextBatch();
                if (batch.isEmpty()) {
                    return true;
                }
                String status;
                try {
                    status = sink.send(taskId, batch);
                } catch (DaemonApiException e) {
                    if (e.isNotFound() || (e.isConflict() && "task.lease_lost".equals(e.code()))) {
                        stop(TaskRunner.LOST);
                        return true;
                    }
                    throw e;
                }
                removeUpTo(batch.getLast().seq());
                if (TaskRunner.isFinished(status)) {
                    stop(status);
                    return true;
                }
            }
        }
    }

    /** Stops the timer and sends what is left, trying a few times. Lines that still cannot be sent are dropped. */
    @Override
    public void close() {
        synchronized (this) {
            if (schedule != null) {
                schedule.cancel(false);
            }
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (flush()) {
                    break;
                }
            } catch (DaemonApiException e) {
                if (attempt == 3 || !e.isRetryable()) {
                    LOG.log(Level.WARNING, "Task {0}: {1} log lines were not sent: {2}", shortId(), pendingCount(), e.getMessage());
                    break;
                }
                sleep(Duration.ofSeconds(attempt));
            }
        }
        synchronized (this) {
            if (dropped > 0) {
                LOG.log(Level.WARNING, "Task {0}: {1} log lines were dropped while the server could not be reached", shortId(), dropped);
            }
        }
    }

    /** How many lines were collected (the next line's number). */
    public synchronized long count() {
        return nextSeq;
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private void flushQuietly() {
        try {
            flush();
            synchronized (this) {
                lastError = null;
            }
        } catch (DaemonApiException e) {
            synchronized (this) {
                // Say it once, not every second while the server is away.
                if (!Objects.equals(lastError, e.getMessage())) {
                    LOG.log(Level.WARNING, "Task {0}: cannot send log lines yet: {1}", shortId(), e.getMessage());
                }
                lastError = e.getMessage();
            }
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Task " + shortId() + ": sending log lines failed", e);
        }
    }

    private synchronized List<TaskMessage> nextBatch() {
        List<TaskMessage> batch = new ArrayList<>();
        int chars = 0;
        for (TaskMessage message : pending) {
            if (batch.size() == MAX_BATCH || (!batch.isEmpty() && chars + message.content().length() > MAX_BATCH_CHARS)) {
                break;
            }
            batch.add(message);
            chars += message.content().length();
        }
        return batch;
    }

    /** Drops the lines that were sent (lines may have been dropped meanwhile, so by number, not by count). */
    private synchronized void removeUpTo(long lastSentSeq) {
        while (!pending.isEmpty() && pending.peekFirst().seq() <= lastSentSeq) {
            pending.removeFirst();
        }
    }

    private void stop(String status) {
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
            pending.clear();
        }
        onStopped.accept(status);
    }

    private String shortId() {
        return TaskRunner.shortId(taskId);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
