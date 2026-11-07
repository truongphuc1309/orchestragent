package com.orchestragent.daemon.provider;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tells a running provider to stop. The daemon cancels when someone cancels the task on the server, when the task
 * is no longer this daemon's, or when the daemon shuts down. A token is cancelled at most once; the first reason
 * wins. Thread-safe.
 */
public final class CancellationToken {

    private final CountDownLatch cancelled = new CountDownLatch(1);
    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();
    private volatile String reason;

    /**
     * Cancels the token and runs the callbacks registered so far, on this thread.
     *
     * @return false when it was already cancelled (nothing happens then)
     */
    public boolean cancel(String why) {
        synchronized (this) {
            if (reason != null) {
                return false;
            }
            reason = Objects.requireNonNullElse(why, "cancelled");
            cancelled.countDown();
        }
        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (RuntimeException ignored) {
                // A failing callback must not keep the others from running.
            }
        }
        return true;
    }

    public boolean isCancelled() {
        return reason != null;
    }

    /** Why it was cancelled, or null while it is not. */
    public String reason() {
        return reason;
    }

    /**
     * Runs {@code callback} when the token is cancelled, for example to kill a process. If it already is, the
     * callback runs at once, on this thread.
     */
    public void onCancel(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        boolean runNow;
        synchronized (this) {
            runNow = reason != null;
            if (!runNow) {
                callbacks.add(callback);
            }
        }
        if (runNow) {
            callback.run();
        }
    }

    /**
     * Waits until the token is cancelled or {@code timeout} has passed: a sleep that a cancellation cuts short.
     *
     * @return true when the token is cancelled
     */
    public boolean await(Duration timeout) throws InterruptedException {
        return cancelled.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
}
