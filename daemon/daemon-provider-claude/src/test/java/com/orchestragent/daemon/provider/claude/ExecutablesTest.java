package com.orchestragent.daemon.provider.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutablesTest {

    @TempDir
    Path root;

    private Path file(String folder, String name) throws Exception {
        Path path = Files.createDirectories(root.resolve(folder)).resolve(name);
        Files.writeString(path, "x");
        path.toFile().setExecutable(true);
        return path;
    }

    private String path(String... folders) {
        return String.join(File.pathSeparator, java.util.Arrays.stream(folders).map(f -> root.resolve(f).toString()).toList());
    }

    @Test
    void onWindowsTriesTheExtensionsInOrderAcrossThePath() throws Exception {
        file("a", "other.exe");
        Path cmd = file("b", "claude.cmd");
        Path exe = file("c", "claude.exe");
        Executables windows = new Executables(path("a", "b", "c"), ".COM;.EXE;.BAT;.CMD", true);

        assertThat(windows.find("claude")).contains(cmd);
        assertThat(new Executables(path("c", "b"), ".COM;.EXE;.BAT;.CMD", true).find("claude")).contains(exe);
    }

    @Test
    void startsCmdFilesThroughCmdExeWithTheLineQuotedByHand() throws Exception {
        Path cmd = file("npm bin", "claude.cmd");
        Path exe = file("c", "claude.exe");
        Executables windows = new Executables("", "", true);

        assertThat(windows.launcherFor(cmd).command(java.util.List.of("--allowedTools", "Bash(git diff:*)", "-p")))
                .containsExactly("cmd.exe", "/d", "/s", "/c",
                        "\"\"" + cmd + "\" --allowedTools \"Bash(git diff:*)\" -p\"");
        assertThat(windows.launcherFor(exe).command(java.util.List.of("-p"))).containsExactly(exe.toString(), "-p");
        assertThat(new Executables("", "", false).launcherFor(cmd).command(java.util.List.of())).containsExactly(cmd.toString());
    }

    @Test
    void refusesWhatCmdExeWouldMangle() throws Exception {
        Executables.Launcher launcher = new Executables("", "", true).launcherFor(file("b", "claude.cmd"));

        assertThatThrownBy(() -> launcher.command(java.util.List.of("100%"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> launcher.command(java.util.List.of("say \"hi\""))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPathIsUsedAsItIsAndMustExist() throws Exception {
        Path exe = file("tools", "claude.exe");
        Executables windows = new Executables("", "", true);

        assertThat(windows.find(exe.toString())).contains(exe);
        assertThat(windows.find(root.resolve("tools").resolve("claude").toString())).contains(exe);
        assertThat(windows.find(root.resolve("nowhere").resolve("claude").toString())).isEmpty();
    }

    @Test
    void elsewhereTheNameIsTheFile() throws Exception {
        Path claude = file("bin", "claude");
        file("bin2", "claude.exe");

        assertThat(new Executables(path("bin2", "bin"), null, false).find("claude")).contains(claude);
        assertThat(new Executables(path("bin2"), null, false).find("claude")).isEmpty();
        assertThat(new Executables("", null, false).find(" ")).isEmpty();
    }
}
