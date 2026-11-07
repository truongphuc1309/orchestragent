package com.orchestragent.daemon.cli.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.orchestragent.daemon.cli.client.Retry;
import com.orchestragent.daemon.cli.testing.FakeDaemonApi;
import com.orchestragent.daemon.cli.testing.FakeServer;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.CompleteRequest;
import com.orchestragent.daemon.protocol.FailRequest;
import com.orchestragent.daemon.protocol.TaskMessage;
import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.ProviderInfo;
import com.orchestragent.daemon.provider.RunListener;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import com.orchestragent.daemon.provider.Usage;
import com.orchestragent.daemon.provider.mock.MockProvider;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskRunnerTest {

    @TempDir
    Path home;

    private final FakeDaemonApi api;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final TaskRunner.Timing timing =
            new TaskRunner.Timing(Duration.ofMillis(20), Duration.ofMillis(30), Duration.ofMinutes(1));
    private final Retry noWaitRetry = new Retry(4, Duration.ofMillis(1), Duration.ofMillis(1), Retry.THREAD_SLEEP);

    TaskRunnerTest() throws Exception {
        api = new FakeDaemonApi();
    }

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
        api.close();
        assertThat(api.server.unhandled()).isEmpty();
    }

    private TaskRunner runner(AgentProvider... providers) {
        Map<String, AgentProvider> byId = new java.util.HashMap<>();
        for (AgentProvider provider : providers) {
            byId.put(provider.id(), provider);
        }
        return new TaskRunner(api.client(), byId, new WorkDirs(home.resolve("workspaces")), new PromptBuilder(),
                scheduler, timing, noWaitRetry);
    }

    private static MockProvider mock() {
        return new MockProvider(Duration.ofMillis(5));
    }

    /** A provider whose run is the given function. */
    private static AgentProvider scripted(BiFunction<RunRequest, RunListener, RunResult> run) {
        return new AgentProvider() {
            @Override
            public String id() {
                return "scripted";
            }

            @Override
            public Optional<ProviderInfo> detect() {
                return Optional.of(new ProviderInfo("1", ""));
            }

            @Override
            public RunResult run(RunRequest request, RunListener listener, CancellationToken cancel) {
                return run.apply(request, listener);
            }
        };
    }

    @Test
    void aSuccessfulRunSendsEveryLineThenCompletesWithTheResult() {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("mock", "Add a README"));

        runner(mock()).run(task, new CancellationToken());

        CompleteRequest completion = api.completions.get(task.taskId());
        assertThat(completion).isNotNull();
        assertThat(completion.output()).contains("Add a README");
        assertThat(completion.sessionId()).startsWith("mock-");
        assertThat(Path.of(completion.workDir())).isDirectory().startsWith(home.resolve("workspaces"));
        assertThat(completion.usage().inputTokens()).isPositive();
        assertThat(completion.usage().costUsd()).isEqualByComparingTo(BigDecimal.ZERO);

        List<TaskMessage> lines = api.messagesOf(task.taskId());
        assertThat(lines).extracting(TaskMessage::seq).containsExactly(
                java.util.stream.LongStream.range(0, lines.size()).boxed().toArray(Long[]::new));
        assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
        assertThat(lines.getFirst().type()).isEqualTo("system");

        // start comes first, and every batch of lines before the completion
        List<String> events = api.events;
        assertThat(events.getFirst()).startsWith("start ");
        assertThat(events.getLast()).startsWith("complete ");
        assertThat(api.statuses.get(task.taskId())).isEqualTo("completed");
    }

    @Test
    void aFailedRunIsReportedWithItsReason() {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("mock", "Broken [mock:fail]"));

        runner(mock()).run(task, new CancellationToken());

        FailRequest failure = api.failures.get(task.taskId());
        assertThat(failure.failureReason()).isEqualTo("agent_error");
        assertThat(failure.error()).contains("Simulated failure");
        assertThat(failure.workDir()).isNotBlank();
        assertThat(api.completions).isEmpty();
        assertThat(api.messagesOf(task.taskId())).anySatisfy(line -> assertThat(line.type()).isEqualTo("error"));
    }

    @Test
    void aProviderThatThrowsFailsTheTask() {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("mock", "Boom [mock:crash]"));

        runner(mock()).run(task, new CancellationToken());

        FailRequest failure = api.failures.get(task.taskId());
        assertThat(failure.failureReason()).isEqualTo("provider_error");
        assertThat(failure.error()).contains("crashed");
        assertThat(api.messagesOf(task.taskId())).last().satisfies(line -> assertThat(line.type()).isEqualTo("error"));
    }

    @Test
    void aTaskForAProviderThisDaemonDoesNotRunFailsWithoutStarting() {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("codex", "T"));

        runner(mock()).run(task, new CancellationToken());

        assertThat(api.failures.get(task.taskId()).failureReason()).isEqualTo("provider_unavailable");
        assertThat(api.events).noneMatch(event -> event.startsWith("start "));
    }

    @Test
    void aTaskCancelledBeforeItStartsIsNotRun() {
        ClaimedTask task = FakeDaemonApi.task("scripted", "T");
        api.statuses.put(task.taskId(), "cancelled");
        AtomicInteger runs = new AtomicInteger();

        runner(scripted((request, listener) -> {
            runs.incrementAndGet();
            return RunResult.succeeded("x", null, null);
        })).run(task, new CancellationToken());

        assertThat(runs).hasValue(0);
        assertThat(api.completions).isEmpty();
        assertThat(api.failures).isEmpty();
    }

    @Test
    void aTaskThatWentBackToTheQueueIsSkipped() {
        ClaimedTask task = api.enqueue(FakeDaemonApi.task("mock", "T"));

        runner(mock()).run(task, new CancellationToken());

        assertThat(api.statuses.get(task.taskId())).isEqualTo("queued");
        assertThat(api.events).containsExactly("start " + task.taskId());
    }

    @Test
    void aCancellationOnTheServerStopsTheRunAndNothingIsReported() throws Exception {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("mock", "Forever [mock:hang]"));
        CancellationToken token = new CancellationToken();
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> runner(mock()).run(task, token));
        await().atMost(Duration.ofSeconds(5)).until(() -> "running".equals(api.statuses.get(task.taskId())));

        api.statuses.put(task.taskId(), "cancelled");

        running.get(10, TimeUnit.SECONDS);
        assertThat(token.reason()).isEqualTo(TaskRunner.CANCELLED);
        assertThat(api.events).noneMatch(event -> event.startsWith("complete ") || event.startsWith("fail "));
    }

    @Test
    void aShutdownStopsTheRunAndReportsIt() throws Exception {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("mock", "Forever [mock:hang]"));
        CancellationToken token = new CancellationToken();
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> runner(mock()).run(task, token));
        await().atMost(Duration.ofSeconds(5)).until(() -> "running".equals(api.statuses.get(task.taskId())));

        token.cancel(TaskRunner.SHUTDOWN);

        running.get(10, TimeUnit.SECONDS);
        assertThat(api.failures.get(task.taskId()).failureReason()).isEqualTo("daemon_stopped");
    }

    @Test
    void theCompletionIsRetriedWhenTheServerIsBrieflyDown() {
        ClaimedTask task = api.dispatched(FakeDaemonApi.task("mock", "T"));
        AtomicInteger attempts = new AtomicInteger();
        api.server.on("POST", "/api/daemon/tasks/" + task.taskId() + "/complete", call -> {
            if (attempts.incrementAndGet() <= 2) {
                return new FakeServer.Reply(503, "");
            }
            api.statuses.put(task.taskId(), "completed");
            return FakeServer.status("completed");
        });

        runner(mock()).run(task, new CancellationToken());

        assertThat(attempts).hasValue(3);
        assertThat(api.statuses.get(task.taskId())).isEqualTo("completed");
    }

    @Test
    void theShortIdIsTheRandomEndOfTheId() {
        // Two UUIDv7 made in the same minute share their first characters (a timestamp), not their last.
        assertThat(TaskRunner.shortId(java.util.UUID.fromString("01a1b711-0000-7000-8000-0000000000aa"))).isEqualTo("000000aa");
        assertThat(TaskRunner.shortId(java.util.UUID.fromString("01a1b711-0000-7000-8000-0000000000bb"))).isEqualTo("000000bb");
    }

    @Test
    void resumesTheEarlierSessionOnlyInTheEarlierFolder() throws Exception {
        AtomicReference<RunRequest> seen = new AtomicReference<>();
        AgentProvider provider = scripted((request, listener) -> {
            seen.set(request);
            listener.onMessage(MessageType.TEXT, "hi");
            return RunResult.succeeded("ok", "session-2", Usage.NONE);
        });
        ClaimedTask first = api.dispatched(FakeDaemonApi.task("scripted", "T"));
        runner(provider).run(first, new CancellationToken());
        String earlierFolder = api.completions.get(first.taskId()).workDir();

        ClaimedTask again = api.dispatched(FakeDaemonApi.task("scripted", "T", "comment", "session-1", earlierFolder));
        runner(provider).run(again, new CancellationToken());
        assertThat(seen.get().priorSessionId()).isEqualTo("session-1");
        assertThat(seen.get().workDir()).isEqualTo(Path.of(earlierFolder).toRealPath());

        ClaimedTask moved = api.dispatched(FakeDaemonApi.task("scripted", "T", "comment", "session-1", earlierFolder + "-gone"));
        runner(provider).run(moved, new CancellationToken());
        assertThat(seen.get().priorSessionId()).isNull();
    }
}
