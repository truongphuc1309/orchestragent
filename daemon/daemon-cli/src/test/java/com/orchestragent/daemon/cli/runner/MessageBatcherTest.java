package com.orchestragent.daemon.cli.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.protocol.TaskMessage;
import com.orchestragent.daemon.provider.MessageType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessageBatcherTest {

    private final UUID taskId = UUID.randomUUID();
    private final List<List<TaskMessage>> sent = new CopyOnWriteArrayList<>();
    private final List<String> stops = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    private MessageBatcher batcher(MessageBatcher.Sink sink) {
        return new MessageBatcher(taskId, sink, stops::add);
    }

    private MessageBatcher.Sink recording(String status) {
        return (id, batch) -> {
            assertThat(id).isEqualTo(taskId);
            sent.add(List.copyOf(batch));
            return status;
        };
    }

    private List<Long> sentSeqs() {
        List<Long> seqs = new ArrayList<>();
        sent.forEach(batch -> batch.forEach(message -> seqs.add(message.seq())));
        return seqs;
    }

    @Test
    void numbersLinesInOrderAndSplitsBatchesAtTheServerLimit() {
        MessageBatcher batcher = batcher(recording("running"));
        for (int i = 0; i < 450; i++) {
            batcher.onMessage(MessageType.TEXT, "line " + i);
        }

        batcher.flush();

        assertThat(sent).extracting(List::size).containsExactly(200, 200, 50);
        assertThat(sentSeqs()).isEqualTo(java.util.stream.LongStream.range(0, 450).boxed().toList());
        assertThat(sent.getFirst().getFirst()).isEqualTo(new TaskMessage(0, "text", "line 0"));
        assertThat(batcher.count()).isEqualTo(450);
    }

    @Test
    void keepsTheTypeCode() {
        MessageBatcher batcher = batcher(recording("running"));
        batcher.onMessage(MessageType.TOOL_RESULT, "ok");

        batcher.flush();

        assertThat(sent.getFirst().getFirst().type()).isEqualTo("tool_result");
    }

    @Test
    void aFailedBatchIsSentAgainWithTheSameNumbers() {
        AtomicInteger attempts = new AtomicInteger();
        MessageBatcher batcher = batcher((id, batch) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new DaemonApiException(503, null, "down");
            }
            sent.add(List.copyOf(batch));
            return "running";
        });
        batcher.onMessage(MessageType.TEXT, "a");
        batcher.onMessage(MessageType.TEXT, "b");

        assertThatThrownBy(batcher::flush).isInstanceOf(DaemonApiException.class);
        batcher.onMessage(MessageType.TEXT, "c");
        batcher.flush();

        assertThat(sentSeqs()).containsExactly(0L, 1L, 2L);
        assertThat(batcher.pendingCount()).isZero();
    }

    @Test
    void sendsOnATimer() {
        MessageBatcher batcher = batcher(recording("running"));
        batcher.start(scheduler, Duration.ofMillis(20));

        batcher.onMessage(MessageType.TEXT, "first");
        await().atMost(Duration.ofSeconds(5)).until(() -> sentSeqs().contains(0L));
        batcher.onMessage(MessageType.TEXT, "second");
        await().atMost(Duration.ofSeconds(5)).until(() -> sentSeqs().contains(1L));

        assertThat(sentSeqs()).containsExactly(0L, 1L);
    }

    @Test
    void closeSendsWhatIsLeft() {
        MessageBatcher batcher = batcher(recording("running"));
        batcher.start(scheduler, Duration.ofHours(1));
        batcher.onMessage(MessageType.TEXT, "last words");

        batcher.close();

        assertThat(sentSeqs()).containsExactly(0L);
    }

    @Test
    void stopsOnceWhenTheServerSaysTheTaskIsCancelled() {
        MessageBatcher batcher = batcher(recording("cancelled"));
        batcher.onMessage(MessageType.TEXT, "a");

        batcher.flush();
        batcher.onMessage(MessageType.TEXT, "b");
        batcher.flush();

        assertThat(stops).containsExactly("cancelled");
        assertThat(sent).hasSize(1);
    }

    @Test
    void aTaskTheServerDoesNotKnowIsLost() {
        MessageBatcher batcher = batcher((id, batch) -> {
            throw new DaemonApiException(404, "task.not_found", "gone");
        });
        batcher.onMessage(MessageType.TEXT, "a");

        assertThat(batcher.flush()).isTrue();

        assertThat(stops).containsExactly(TaskRunner.LOST);
        assertThat(batcher.pendingCount()).isZero();
    }

    @Test
    void cutsVeryLongLines() {
        MessageBatcher batcher = batcher(recording("running"));
        batcher.onMessage(MessageType.TOOL_RESULT, "x".repeat(MessageBatcher.MAX_CONTENT + 10));

        batcher.flush();

        String content = sent.getFirst().getFirst().content();
        assertThat(content).hasSize(MessageBatcher.MAX_CONTENT).endsWith("(cut: too long)");
    }

    @Test
    void keepsOnlyTheNewestLinesWhileTheServerIsAway() {
        MessageBatcher batcher = batcher(recording("running"));
        for (int i = 0; i < MessageBatcher.MAX_PENDING + 5; i++) {
            batcher.onMessage(MessageType.TEXT, "l");
        }

        assertThat(batcher.pendingCount()).isEqualTo(MessageBatcher.MAX_PENDING);
        batcher.flush();
        assertThat(sentSeqs().getFirst()).isEqualTo(5L);
    }
}
