package com.orchestragent.daemon.protocol;

import java.util.List;
import java.util.UUID;

/**
 * A task handed to the daemon, with everything it needs to run it. {@code trigger} is assigned, comment,
 * changes_requested or retry. When the agent has worked on the issue before, {@code priorSessionId} and
 * {@code priorWorkDir} say which session of the AI tool to resume and in which folder (the tool keeps its
 * sessions per folder, so the same folder must be used).
 */
public record ClaimedTask(
        UUID taskId,
        UUID workspaceId,
        UUID runtimeId,
        String provider,
        String trigger,
        int attempt,
        AgentInfo agent,
        IssueInfo issue,
        List<CommentInfo> comments,
        String priorSessionId,
        String priorWorkDir) {
}
