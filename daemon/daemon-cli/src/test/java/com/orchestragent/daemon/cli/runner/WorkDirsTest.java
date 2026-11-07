package com.orchestragent.daemon.cli.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.daemon.cli.testing.FakeDaemonApi;
import com.orchestragent.daemon.protocol.ClaimedTask;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkDirsTest {

    @TempDir
    Path home;

    private WorkDirs workDirs() {
        return new WorkDirs(home.resolve("workspaces"));
    }

    private static ClaimedTask withPrior(String priorWorkDir) {
        return FakeDaemonApi.task("mock", "T", "comment", "session-1", priorWorkDir);
    }

    @Test
    void makesAFolderPerWorkspaceIssueAndTask() throws Exception {
        ClaimedTask task = FakeDaemonApi.task("mock", "T");

        WorkDirs.WorkDir dir = workDirs().prepare(task);

        assertThat(dir.reused()).isFalse();
        assertThat(dir.path()).isDirectory();
        assertThat(dir.path()).isEqualTo(home.resolve("workspaces").resolve(task.workspaceId().toString())
                .resolve("12-" + task.taskId().toString().substring(28)).toAbsolutePath());
    }

    @Test
    void reusesTheEarlierFolderWhenItIsStillThere() throws Exception {
        ClaimedTask first = FakeDaemonApi.task("mock", "T");
        Path earlier = workDirs().prepare(first).path();

        WorkDirs.WorkDir dir = workDirs().prepare(withPrior(earlier.toString()));

        assertThat(dir.reused()).isTrue();
        assertThat(dir.path()).isEqualTo(earlier.toRealPath());
    }

    @Test
    void makesANewFolderWhenTheEarlierOneIsGone() throws Exception {
        Path gone = home.resolve("workspaces").resolve(FakeDaemonApi.task("mock", "T").workspaceId().toString()).resolve("old");

        WorkDirs.WorkDir dir = workDirs().prepare(withPrior(gone.toString()));

        assertThat(dir.reused()).isFalse();
        assertThat(dir.path()).isNotEqualTo(gone);
    }

    @Test
    void neverUsesAFolderOutsideTheRoot() throws Exception {
        Path outside = Files.createDirectories(home.resolve("elsewhere"));
        Path sneaky = home.resolve("workspaces").resolve("..").resolve("elsewhere");

        assertThat(workDirs().prepare(withPrior(outside.toString())).reused()).isFalse();
        assertThat(workDirs().prepare(withPrior(sneaky.toString())).reused()).isFalse();
        assertThat(workDirs().prepare(withPrior("relative/path")).reused()).isFalse();
    }

    @Test
    void neverUsesAFolderOfAnotherWorkspace() throws Exception {
        Path other = Files.createDirectories(home.resolve("workspaces").resolve("another-workspace").resolve("1-abc"));

        assertThat(workDirs().prepare(withPrior(other.toString())).reused()).isFalse();
    }
}
