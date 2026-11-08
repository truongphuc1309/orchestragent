package com.orchestragent.agent.application;

import com.orchestragent.agent.application.Views.RuntimeSummary;
import com.orchestragent.agent.domain.MessageType;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.agent.domain.TaskTrigger;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models of agent tasks: what people see in the web app and what a daemon is handed when it claims work. */
public final class TaskViews {

    private TaskViews() {
    }

    /** A task as the web app shows it. */
    public record TaskView(
            UUID id,
            UUID issueId,
            UUID agentId,
            String agentName,
            RuntimeSummary runtime,
            TaskTrigger trigger,
            TaskStatus status,
            int attempt,
            String error,
            String failureReason,
            Long inputTokens,
            Long outputTokens,
            BigDecimal costUsd,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {
    }

    public record TaskMessageView(long seq, MessageType type, String content, Instant createdAt) {
    }

    // ---- what a daemon gets when it claims a task --------------------------------------------------------

    public record AgentBrief(UUID id, String name, String instructions, String model) {
    }

    /** {@code key} is the issue's human-friendly name, {@code WEB-12}. */
    public record IssueBrief(UUID id, String key, int number, String title, String description) {
    }

    /** {@code authorType} is member, agent or system. */
    public record CommentBrief(String authorType, String authorName, String body, Instant createdAt) {
    }

    /**
     * Everything a daemon needs to run a task: who the agent is, the issue and its comments, and, when this
     * agent has worked on the issue before, the session and folder to resume. {@code provider} says which AI
     * tool of the daemon's computer should do the work.
     */
    public record ClaimedTask(
            UUID taskId,
            UUID workspaceId,
            UUID runtimeId,
            String provider,
            TaskTrigger trigger,
            int attempt,
            AgentBrief agent,
            IssueBrief issue,
            List<CommentBrief> comments,
            String priorSessionId,
            String priorWorkDir) {
    }
}
