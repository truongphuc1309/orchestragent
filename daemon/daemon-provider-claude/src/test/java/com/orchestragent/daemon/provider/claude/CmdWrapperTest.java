package com.orchestragent.daemon.provider.claude;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.ProviderSettings;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * npm installs Claude Code on Windows as {@code claude.cmd}. This starts the fake CLI through such a file, found on
 * a {@code PATH}, with arguments that {@code cmd.exe} could garble (spaces, parentheses, a colon and an asterisk).
 */
@EnabledOnOs(OS.WINDOWS)
class CmdWrapperTest {

    @TempDir
    Path root;

    @Test
    void runsAClaudeCmdWithAwkwardArguments() throws Exception {
        Path bin = Files.createDirectories(root.resolve("npm bin"));
        Path classes = Path.of(FakeClaude.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String java = ProcessHandle.current().info().command().orElseThrow();
        Files.writeString(bin.resolve("claude.cmd"),
                "@echo off\r\n\"" + java + "\" -cp \"" + classes + "\" " + FakeClaude.class.getName() + " %*\r\n");
        Path workDir = Files.createDirectories(root.resolve("work dir"));

        ClaudeProvider provider = new ClaudeProvider(new Executables(bin.toString(), ".EXE;.CMD", true), null);
        provider.configure(new ProviderSettings("claude", Map.of("allowedTools", List.of("Bash(git diff:*)", "Read"))));

        assertThat(provider.detect()).hasValueSatisfying(info -> {
            assertThat(info.version()).isEqualTo("9.9.9");
            assertThat(info.location()).endsWith("claude.cmd");
        });
        RunResult result = provider.run(new RunRequest("Say hi & exit | now", workDir, null, null, Duration.ofMinutes(2)),
                (type, content) -> { }, new CancellationToken());

        assertThat(result.isSuccess()).as(result.error()).isTrue();
        assertThat(Files.readAllLines(workDir.resolve(FakeClaude.ARGS_FILE), StandardCharsets.UTF_8))
                .containsSubsequence("--allowedTools", "Bash(git diff:*)", "Read");
        assertThat(Files.readString(workDir.resolve(FakeClaude.PROMPT_FILE))).isEqualTo("Say hi & exit | now");
    }
}
