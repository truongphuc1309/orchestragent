package com.orchestragent.daemon.cli.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.provider.CancellationToken;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CancellationWatcherTest {

    private final UUID taskId = UUID.randomUUID();
    private final CancellationToken token = new CancellationToken();

    private CancellationWatcher watcher(Function<UUID, String> status) {
        return new CancellationWatcher(taskId, status, token);
    }

    @Test
    void aRunningTaskGoesOn() {
        watcher(id -> "running").check();

        assertThat(token.isCancelled()).isFalse();
    }

    @Test
    void aCancelledTaskIsCancelled() {
        watcher(id -> "cancelled").check();

        assertThat(token.reason()).isEqualTo(TaskRunner.CANCELLED);
    }

    @Test
    void aTaskTheServerFinishedOrRequeuedIsLost() {
        watcher(id -> "failed").check();
        assertThat(token.reason()).isEqualTo(TaskRunner.LOST);

        CancellationToken other = new CancellationToken();
        new CancellationWatcher(taskId, id -> "queued", other).check();
        assertThat(other.reason()).isEqualTo(TaskRunner.LOST);
    }

    @Test
    void anUnknownTaskIsLost() {
        watcher(id -> {
            throw new DaemonApiException(404, "task.not_found", "gone");
        }).check();

        assertThat(token.reason()).isEqualTo(TaskRunner.LOST);
    }

    @Test
    void anUnreachableServerDoesNotStopTheRun() {
        watcher(id -> {
            throw new DaemonApiException(0, null, "no network");
        }).check();

        assertThat(token.isCancelled()).isFalse();
    }

    @Test
    void pollsUntilClosed() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger polls = new AtomicInteger();
        try (CancellationWatcher watcher = watcher(id -> polls.incrementAndGet() >= 3 ? "cancelled" : "running")) {
            watcher.start(scheduler, Duration.ofMillis(10));

            await().atMost(Duration.ofSeconds(5)).until(token::isCancelled);
        } finally {
            scheduler.shutdownNow();
        }
        assertThat(polls).hasValueGreaterThanOrEqualTo(3);
    }
}
