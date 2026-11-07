package com.orchestragent.daemon.cli.client;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Repeats a server call that failed for a reason that may go away (see {@link DaemonApiException#isRetryable()}),
 * waiting longer each time: {@code initialDelay}, twice that, and so on up to {@code maxDelay}. Other failures are
 * thrown at once. Only for calls that are safe to repeat.
 */
public record Retry(int attempts, Duration initialDelay, Duration maxDelay, Sleeper sleeper) {

    /** Waits; replaced in tests so they do not sleep. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public static final Sleeper THREAD_SLEEP = duration -> Thread.sleep(duration);

    /** For reporting the end of a task: about a minute and a half of trying before giving up. */
    public static Retry forReports() {
        return new Retry(7, Duration.ofSeconds(1), Duration.ofSeconds(30), THREAD_SLEEP);
    }

    public Retry {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1");
        }
    }

    public <T> T call(Supplier<T> action) {
        Duration delay = initialDelay;
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (DaemonApiException e) {
                if (!e.isRetryable() || attempt >= attempts) {
                    throw e;
                }
            }
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DaemonApiException(0, null, "Interrupted while waiting to retry", e);
            }
            delay = delay.multipliedBy(2).compareTo(maxDelay) > 0 ? maxDelay : delay.multipliedBy(2);
        }
    }
}
