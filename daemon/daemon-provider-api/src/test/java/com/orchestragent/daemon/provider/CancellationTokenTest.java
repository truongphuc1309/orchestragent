package com.orchestragent.daemon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CancellationTokenTest {

    @Test
    void startsNotCancelled() throws InterruptedException {
        CancellationToken token = new CancellationToken();

        assertThat(token.isCancelled()).isFalse();
        assertThat(token.reason()).isNull();
        assertThat(token.await(Duration.ofMillis(10))).isFalse();
    }

    @Test
    void theFirstReasonWinsAndCallbacksRunOnce() throws InterruptedException {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(calls::incrementAndGet);

        assertThat(token.cancel("user")).isTrue();
        assertThat(token.cancel("shutdown")).isFalse();

        assertThat(token.reason()).isEqualTo("user");
        assertThat(calls).hasValue(1);
        assertThat(token.await(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void aCallbackAddedAfterCancellationRunsAtOnce() {
        CancellationToken token = new CancellationToken();
        token.cancel("user");
        AtomicInteger calls = new AtomicInteger();

        token.onCancel(calls::incrementAndGet);

        assertThat(calls).hasValue(1);
    }

    @Test
    void aFailingCallbackDoesNotStopTheOthers() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(() -> {
            throw new IllegalStateException("boom");
        });
        token.onCancel(calls::incrementAndGet);

        token.cancel("user");

        assertThat(calls).hasValue(1);
    }

    @Test
    void awaitReturnsAsSoonAsAnotherThreadCancels() throws InterruptedException {
        CancellationToken token = new CancellationToken();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            token.cancel("user");
        });

        long start = System.nanoTime();
        boolean cancelled = token.await(Duration.ofSeconds(10));

        assertThat(cancelled).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }
}
