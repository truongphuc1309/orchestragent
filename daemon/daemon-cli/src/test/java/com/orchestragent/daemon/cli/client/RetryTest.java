package com.orchestragent.daemon.cli.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

    private final List<Duration> waits = new CopyOnWriteArrayList<>();
    private final Retry retry = new Retry(5, Duration.ofSeconds(1), Duration.ofSeconds(3), waits::add);

    @Test
    void retriesARetryableFailureWithGrowingWaits() {
        AtomicInteger calls = new AtomicInteger();

        String result = retry.call(() -> {
            if (calls.incrementAndGet() < 4) {
                throw new DaemonApiException(503, null, "down");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(4);
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            calls.incrementAndGet();
            throw new DaemonApiException(0, null, "no network");
        })).isInstanceOf(DaemonApiException.class);

        assertThat(calls).hasValue(5);
        assertThat(waits).hasSize(4);
    }

    @Test
    void doesNotRetryAClientError() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.call(() -> {
            calls.incrementAndGet();
            throw new DaemonApiException(409, "task.invalid_state", "conflict");
        })).isInstanceOf(DaemonApiException.class);

        assertThat(calls).hasValue(1);
        assertThat(waits).isEmpty();
    }
}
