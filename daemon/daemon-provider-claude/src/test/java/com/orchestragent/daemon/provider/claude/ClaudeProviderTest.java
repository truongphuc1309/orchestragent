package com.orchestragent.daemon.provider.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.awaitility.Awaitility.await;

import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.ProviderSettings;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the provider against {@link FakeClaude}, a separate process that plays the CLI. Never runs the real one.
 * Every test has a time limit, so a provider that fails to stop a run makes a test fail instead of hang.
 */
@Timeout(60)
class ClaudeProviderTest {

    @TempDir
    Path workDir;

    private final List<String> log = new CopyOnWriteArrayList<>();

    /** {@code java -cp <test classes> FakeClaude}: the fake needs nothing but its own class. */
    static List<String> fakeCommand() {
        try {
            String java = ProcessHandle.current().info().command().orElse("java");
            Path classes = Path.of(FakeClaude.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return List.of(java, "-cp", classes.toString(), FakeClaude.class.getName());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClaudeProvider provider(Map<String, Object> settings) {
        ClaudeProvider provider = new ClaudeProvider(Executables.fromEnvironment(), fakeCommand());
        provider.configure(new ProviderSettings("claude", settings));
        return provider;
    }

    private RunResult run(ClaudeProvider provider, String prompt, String model, String resume, Duration timeout, CancellationToken cancel) {
        return provider.run(new RunRequest(prompt, workDir, model, resume, timeout),
                (type, content) -> log.add(type.code() + ": " + content), cancel);
    }

    private RunResult run(String prompt) {
        return run(provider(Map.of()), prompt, null, null, Duration.ofMinutes(2), new CancellationToken());
    }

    private List<String> argsSeen() throws Exception {
        return Files.readAllLines(workDir.resolve(FakeClaude.ARGS_FILE), StandardCharsets.UTF_8);
    }

    @Test
    void isFoundByTheServiceLoaderAndRunsByDefault() {
        List<AgentProvider> providers = ServiceLoader.load(AgentProvider.class).stream().map(ServiceLoader.Provider::get).toList();

        assertThat(providers).anySatisfy(provider -> {
            assertThat(provider.id()).isEqualTo("claude");
            assertThat(provider.enabledByDefault()).isTrue();
        });
    }

    @Test
    void detectsTheVersion() {
        assertThat(provider(Map.of()).detect()).hasValueSatisfying(info -> assertThat(info.version()).isEqualTo("9.9.9"));
    }

    @Test
    void aRunSendsThePromptOnStandardInputAndReturnsTheResult() throws Exception {
        RunResult result = run(provider(Map.of()), "Fix issue #3\nscenario:ok", "sonnet", null, Duration.ofMinutes(2),
                new CancellationToken());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("All done in " + workDir.getFileName());
        assertThat(result.sessionId()).isEqualTo("fake-session-1");
        assertThat(result.usage().inputTokens()).isEqualTo(110L);
        assertThat(result.usage().outputTokens()).isEqualTo(5L);
        assertThat(result.usage().costUsd()).isEqualByComparingTo(new BigDecimal("0.0123"));
        assertThat(Files.readString(workDir.resolve(FakeClaude.PROMPT_FILE))).isEqualTo("Fix issue #3\nscenario:ok");
        assertThat(argsSeen()).containsExactly("-p", "--output-format", "stream-json", "--verbose",
                "--permission-mode", "acceptEdits", "--model", "sonnet", "--disallowedTools", "AskUserQuestion");
        assertThat(log).containsExactly(
                "system: Claude Code 9.9.9 started (session fake-session-1, model sonnet, permission mode acceptEdits)",
                "text: Working on it",
                "tool_use: Write notes.md\n{\"file_path\":\"notes.md\"}",
                "tool_result: File created");
    }

    @Test
    void passesTheSettingsAndTheSessionToResume() throws Exception {
        ClaudeProvider provider = provider(Map.of(
                "permissionMode", "plan",
                "allowedTools", List.of("Bash(npm test:*)", "Read"),
                "disallowedTools", List.of("WebFetch")));

        RunResult result = run(provider, "go", null, "earlier-session", Duration.ofMinutes(2), new CancellationToken());

        assertThat(result.sessionId()).isEqualTo("earlier-session");
        assertThat(argsSeen()).containsExactly("-p", "--output-format", "stream-json", "--verbose",
                "--permission-mode", "plan", "--resume", "earlier-session",
                "--allowedTools", "Bash(npm test:*)", "Read", "--disallowedTools", "AskUserQuestion", "WebFetch");
    }

    @Test
    void aRefusedResumeStartsOnceMoreWithANewSession() throws Exception {
        RunResult result = run(provider(Map.of()), "go", null, FakeClaude.MISSING_SESSION, Duration.ofMinutes(2),
                new CancellationToken());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.sessionId()).isEqualTo("fake-session-1");
        assertThat(argsSeen()).doesNotContain("--resume");
        assertThat(log.getFirst()).isEqualTo("system: The earlier session missing could not be resumed; starting a new session");
    }

    @Test
    void anErrorResultFailsTheRun() {
        RunResult result = run("scenario:error");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(RunResult.AGENT_ERROR);
        assertThat(result.error()).isEqualTo("Something broke");
        assertThat(result.sessionId()).isEqualTo("fake-session-1");
        assertThat(log).last().isEqualTo("error: Something broke");
    }

    @Test
    void aCrashWithoutResultFailsWithTheExitCodeAndStandardError() {
        RunResult result = run("scenario:crash");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("exited with code 3").contains("boom: the fake CLI crashed");
    }

    @Test
    void noiseOnStandardOutputIsSkipped() {
        RunResult result = run("scenario:garbage");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("Done despite the noise");
    }

    @Test
    void cancellingKillsTheWholeProcessTree() throws Exception {
        CancellationToken cancel = new CancellationToken();
        CompletableFuture<RunResult> running = CompletableFuture.supplyAsync(
                () -> run(provider(Map.of()), "scenario:hang", null, null, Duration.ofMinutes(5), cancel));
        Path pidFile = workDir.resolve(FakeClaude.CHILD_PID_FILE);
        await().atMost(Duration.ofSeconds(20)).until(() -> Files.exists(pidFile) && !Files.readString(pidFile).isBlank());
        long childPid = Long.parseLong(Files.readString(pidFile).strip());
        assertThat(ProcessHandle.of(childPid)).hasValueSatisfying(child -> assertThat(child.isAlive()).isTrue());

        try {
            cancel.cancel("user");

            RunResult result = assertTimeoutPreemptively(Duration.ofSeconds(40), () -> running.get());
            assertThat(result.failureReason()).isEqualTo("cancelled");
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false) == Boolean.FALSE);
        } finally {
            // Never leave the child behind, even when the test fails.
            ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void aProcessLeftBehindIsKilledWhenTheRunEnds() throws Exception {
        try {
            RunResult result = assertTimeoutPreemptively(Duration.ofSeconds(40), () -> run("scenario:orphan"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).isEqualTo("Done, a child is left behind");
            long childPid = Long.parseLong(Files.readString(workDir.resolve(FakeClaude.CHILD_PID_FILE)).strip());
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false) == Boolean.FALSE);
        } finally {
            killLeftoverChild();
        }
    }

    @Test
    void aProcessThatEscapesDoesNotKeepTheRunWaiting() throws Exception {
        try {
            // Started and left in a few milliseconds: maybe never seen, maybe not killable. The run must end anyway.
            RunResult result = assertTimeoutPreemptively(Duration.ofSeconds(40), () -> run("scenario:escape"));

            assertThat(result.isSuccess()).isTrue();
        } finally {
            // The child may have been killed before it even wrote its PID; then there is nothing to clean up.
            killLeftoverChild();
        }
    }

    @Test
    void aSilentRunIsStoppedAfterTheIdleTimeout() throws Exception {
        ClaudeProvider provider = provider(Map.of());
        // Minutes are the smallest unit users can set; tests use a shorter one.
        setIdleTimeout(provider, Duration.ofMillis(1500));

        try {
            RunResult result = assertTimeoutPreemptively(Duration.ofSeconds(50),
                    () -> run(provider, "scenario:hang", null, null, Duration.ofSeconds(20), new CancellationToken()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.failureReason()).isEqualTo("idle_timeout");
        } finally {
            killLeftoverChild();
        }
    }

    @Test
    void aRunThatKeepsTalkingStillStopsAtTheTimeout() {
        RunResult result = assertTimeoutPreemptively(Duration.ofSeconds(40),
                () -> run(provider(Map.of()), "scenario:chatty", null, null, Duration.ofSeconds(3), new CancellationToken()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(RunResult.TIMEOUT);
        assertThat(result.sessionId()).isEqualTo("fake-session-1");
    }

    @Test
    void aMissingProgramIsReported() {
        ClaudeProvider provider = new ClaudeProvider(new Executables("", "", false), null);

        assertThat(provider.detect()).isEmpty();
        RunResult result = provider.run(new RunRequest("go", workDir, null, null, Duration.ofMinutes(1)), (t, c) -> { }, new CancellationToken());
        assertThat(result.failureReason()).isEqualTo("provider_unavailable");
    }

    @Test
    void rejectsWrongSettings() {
        assertThatThrownBy(() -> provider(Map.of("permissionMode", "yolo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claude.permissionMode");
        assertThatThrownBy(() -> provider(Map.of("allowedTools", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claude.allowedTools");
        assertThatThrownBy(() -> provider(Map.of("idleTimeoutMinutes", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(provider(Map.of("permissionMode", "bypassPermissions")).settings().bypassesPermissions()).isTrue();
    }

    @Test
    void doesNotPassOnTheMarkersOfARunningClaudeCode() {
        Map<String, String> environment = new HashMap<>(Map.of(
                "CLAUDECODE", "1", "CLAUDE_CODE_ENTRYPOINT", "cli", "CLAUDECODE_X", "y",
                "CLAUDE_CODE_GIT_BASH_PATH", "C:/git/bash.exe", "PATH", "/bin"));

        ClaudeProvider.cleanEnvironment(environment);

        assertThat(environment).containsOnlyKeys("CLAUDE_CODE_GIT_BASH_PATH", "PATH");
    }

    /** The hang scenario starts a child; the provider kills it, but a broken provider must not leave it running. */
    private void killLeftoverChild() throws Exception {
        Path pidFile = workDir.resolve(FakeClaude.CHILD_PID_FILE);
        if (Files.exists(pidFile) && !Files.readString(pidFile).isBlank()) {
            ProcessHandle.of(Long.parseLong(Files.readString(pidFile).strip())).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    private static void setIdleTimeout(ClaudeProvider provider, Duration idle) {
        ClaudeSettings current = provider.settings();
        provider.use(new ClaudeSettings(current.command(), current.permissionMode(), current.allowedTools(),
                current.disallowedTools(), idle));
    }
}
