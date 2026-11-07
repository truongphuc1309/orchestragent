package com.orchestragent.daemon.provider.claude;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.ProviderSettings;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real Claude Code CLI on the smallest possible task, then resumes the session. It costs money, so it runs
 * only with {@code ORCHESTRAGENT_RUN_REAL_CLAUDE=1}:
 * {@code ORCHESTRAGENT_RUN_REAL_CLAUDE=1 ./gradlew :daemon:daemon-provider-claude:test --tests '*RealClaudeSmokeTest'}.
 */
@EnabledIfEnvironmentVariable(named = "ORCHESTRAGENT_RUN_REAL_CLAUDE", matches = "1")
class RealClaudeSmokeTest {

    @TempDir
    Path workDir;

    @Test
    void answersAndResumes() {
        ClaudeProvider provider = new ClaudeProvider();
        provider.configure(new ProviderSettings("claude", Map.of("permissionMode", "plan")));
        assertThat(provider.detect()).isPresent();
        List<String> log = new CopyOnWriteArrayList<>();

        RunResult first = provider.run(
                new RunRequest("Reply with exactly the word: pong", workDir, "haiku", null, Duration.ofMinutes(3)),
                (type, content) -> log.add(type.code() + ": " + content), new CancellationToken());

        assertThat(first.isSuccess()).as(first.error()).isTrue();
        assertThat(first.output().toLowerCase()).contains("pong");
        assertThat(first.sessionId()).isNotBlank();
        assertThat(first.usage().outputTokens()).isPositive();
        assertThat(log.getFirst()).startsWith("system: Claude Code");

        RunResult second = provider.run(
                new RunRequest("What word did you reply with before? Answer with that word only.", workDir, "haiku",
                        first.sessionId(), Duration.ofMinutes(3)),
                (type, content) -> log.add(type.code() + ": " + content), new CancellationToken());

        assertThat(second.isSuccess()).as(second.error()).isTrue();
        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.output().toLowerCase()).contains("pong");
    }
}
