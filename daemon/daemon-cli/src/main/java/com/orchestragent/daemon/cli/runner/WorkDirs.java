package com.orchestragent.daemon.cli.runner;

import com.orchestragent.daemon.protocol.ClaimedTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Chooses the folder a task works in. All task folders live under one root ({@code ~/.orchestragent/workspaces}).
 * A task that continues earlier work gets the earlier folder back when it still exists, because AI tools keep their
 * sessions per folder; otherwise a new one is made: {@code <root>/<workspaceId>/<issueNumber>-<end of the task ID>}.
 *
 * <p>The earlier folder comes from the server, so it is used only when it is inside the root, in the folder of the
 * task's workspace: a folder elsewhere on the computer is never handed to an agent.
 */
public final class WorkDirs {

    /** The folder, and whether it is the earlier run's (only then can the earlier session be resumed). */
    public record WorkDir(Path path, boolean reused) {
    }

    private final Path root;

    public WorkDirs(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public WorkDir prepare(ClaimedTask task) throws IOException {
        Files.createDirectories(root);
        Path workspaceRoot = root.resolve(task.workspaceId().toString());
        Path prior = earlier(task.priorWorkDir(), workspaceRoot);
        if (prior != null) {
            return new WorkDir(prior, true);
        }
        Path fresh = workspaceRoot.resolve(task.issue().number() + "-" + TaskRunner.shortId(task.taskId()));
        Files.createDirectories(fresh);
        return new WorkDir(fresh, false);
    }

    /** The earlier folder if it still exists and is a folder under the workspace's root (links resolved), else null. */
    private static Path earlier(String priorWorkDir, Path workspaceRoot) throws IOException {
        if (priorWorkDir == null || priorWorkDir.isBlank()) {
            return null;
        }
        Path candidate;
        try {
            candidate = Path.of(priorWorkDir);
        } catch (InvalidPathException e) {
            return null;
        }
        if (!candidate.isAbsolute() || !Files.isDirectory(candidate)) {
            return null;
        }
        if (!Files.isDirectory(workspaceRoot)) {
            return null;
        }
        Path real = candidate.toRealPath();
        Path realRoot = workspaceRoot.toRealPath();
        return real.startsWith(realRoot) && !real.equals(realRoot) ? real : null;
    }
}
