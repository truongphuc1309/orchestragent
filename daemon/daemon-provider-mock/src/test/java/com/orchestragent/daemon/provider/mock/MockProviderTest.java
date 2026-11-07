package com.orchestragent.daemon.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockProviderTest {

    private static final String PROMPT = """
            You are Builder.

            # Issue #7: Add a README

            Describe the project.
            """;

    @TempDir
    Path workDir;

    private final MockProvider provider = new MockProvider(Duration.ofMillis(5));
    private final List<String> log = new CopyOnWriteArrayList<>();

    private RunResult run(String prompt, CancellationToken cancel, Duration timeout) {
        return provider.run(
                new RunRequest(prompt, workDir, "fast", null, timeout),
                (type, content) -> log.add(type.code() + ": " + content),
                cancel);
    }

    @Test
    void isFoundByTheServiceLoader() {
        List<String> ids = ServiceLoader.load(AgentProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(AgentProvider::id)
                .toList();

        assertThat(ids).contains("mock");
        assertThat(provider.detect()).hasValueSatisfying(info -> assertThat(info.version()).isEqualTo(MockProvider.VERSION));
    }

    @Test
    void aNormalRunSendsSeveralLinesAndAnswersWithTheIssueTitle() throws Exception {
        RunResult result = run(PROMPT, new CancellationToken(), Duration.ofMinutes(1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("Add a README");
        assertThat(result.sessionId()).startsWith("mock-");
        assertThat(result.usage().inputTokens()).isPositive();
        assertThat(log).hasSizeBetween(3, 6);
        assertThat(log.getFirst()).startsWith("system: ").contains("model fast");
        assertThat(log).anySatisfy(line -> assertThat(line).startsWith(MessageType.TOOL_USE.code()));
        assertThat(Files.readString(workDir.resolve(MockProvider.NOTES_FILE))).contains("Add a README");
    }

    @Test
    void aResumedRunKeepsTheSessionAndCountsTheRunsInTheFolder() {
        RunResult first = run(PROMPT, new CancellationToken(), Duration.ofMinutes(1));
        RunResult second = provider.run(
                new RunRequest(PROMPT, workDir, null, first.sessionId(), Duration.ofMinutes(1)),
                (type, content) -> log.add(content),
                new CancellationToken());

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.output()).contains("run 2");
    }

    @Test
    void theFailMarkerFailsTheRun() {
        RunResult result = run(PROMPT + "\nPlease [mock:fail]", new CancellationToken(), Duration.ofMinutes(1));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(RunResult.AGENT_ERROR);
        assertThat(log).anySatisfy(line -> assertThat(line).startsWith("error: "));
    }

    @Test
    void theLastMarkerWins() {
        RunResult result = run(PROMPT + "\n[mock:fail]\nlater comment: [mock:ok]", new CancellationToken(), Duration.ofMinutes(1));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void theCrashMarkerThrows() {
        assertThatThrownBy(() -> run(PROMPT + "[mock:crash]", new CancellationToken(), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aHangingRunStopsWhenCancelled() throws Exception {
        CancellationToken cancel = new CancellationToken();
        CompletableFuture<RunResult> running =
                CompletableFuture.supplyAsync(() -> run(PROMPT + "[mock:hang]", cancel, Duration.ofMinutes(5)));
        Thread.sleep(100);
        assertThat(running).isNotDone();

        cancel.cancel("user");

        RunResult result = running.get(5, TimeUnit.SECONDS);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo("cancelled");
    }

    @Test
    void aHangingRunTimesOut() {
        RunResult result = run(PROMPT + "[mock:hang]", new CancellationToken(), Duration.ofMillis(200));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(RunResult.TIMEOUT);
    }

    @Test
    void findsTheTitleOrFallsBackToTheFirstLine() {
        assertThat(MockProvider.titleOf(PROMPT)).isEqualTo("Add a README");
        assertThat(MockProvider.titleOf("\n  Just do it  \nmore")).isEqualTo("Just do it");
    }

    @Test
    void readsTheStepFromTheEnvironment() {
        assertThat(MockProvider.stepFromEnvironment(null)).isEqualTo(MockProvider.DEFAULT_STEP);
        assertThat(MockProvider.stepFromEnvironment("abc")).isEqualTo(MockProvider.DEFAULT_STEP);
        assertThat(MockProvider.stepFromEnvironment("250")).isEqualTo(Duration.ofMillis(250));
    }
}
