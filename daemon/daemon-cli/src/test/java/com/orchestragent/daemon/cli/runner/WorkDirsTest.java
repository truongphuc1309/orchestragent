package com.orchestragent.daemon.cli.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.daemon.cli.testing.FakeDaemonApi;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.IssueInfo;
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
        // Named after the issue key since step 13 (it was the bare number before issues were numbered per project).
        assertThat(dir.path()).isEqualTo(home.resolve("workspaces").resolve(task.workspaceId().toString())
                .resolve("WEB-12-" + task.taskId().toString().substring(28)).toAbsolutePath());
    }

    @Test
    void aMissingOrStrangeKeyFallsBackToTheNumber() throws Exception {
        for (String key : new String[] {null, "", "../../evil", "WEB 12", "a/b"}) {
            ClaimedTask task = withKey(FakeDaemonApi.task("mock", "T"), key);

            WorkDirs.WorkDir dir = workDirs().prepare(task);

            assertThat(dir.path().getFileName()).as(String.valueOf(key))
                    .hasToString("12-" + task.taskId().toString().substring(28));
            assertThat(dir.path().getParent())
                    .isEqualTo(home.resolve("workspaces").resolve(task.workspaceId().toString()).toAbsolutePath());
        }
    }

    private static ClaimedTask withKey(ClaimedTask task, String key) {
        IssueInfo issue = task.issue();
        return new ClaimedTask(task.taskId(), task.workspaceId(), task.runtimeId(), task.provider(), task.trigger(),
                task.attempt(), task.agent(), new IssueInfo(issue.id(), key, issue.number(), issue.title(), issue.description()),
                task.comments(), task.priorSessionId(), task.priorWorkDir());
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
