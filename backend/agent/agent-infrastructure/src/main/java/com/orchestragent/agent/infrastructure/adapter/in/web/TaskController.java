package com.orchestragent.agent.infrastructure.adapter.in.web;

import com.orchestragent.agent.application.TaskService;
import com.orchestragent.agent.application.TaskViews.TaskMessageView;
import com.orchestragent.agent.application.TaskViews.TaskView;
import com.orchestragent.platform.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the web app does with the tasks of an issue: list them, read their log, cancel, retry. */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/tasks")
class TaskController {

    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    record RuntimeSummaryResponse(UUID id, String deviceName, String provider, String status) {
    }

    record TaskResponse(
            UUID id,
            UUID issueId,
            UUID agentId,
            String agentName,
            RuntimeSummaryResponse runtime,
            String trigger,
            String status,
            int attempt,
            String error,
            String failureReason,
            Long inputTokens,
            Long outputTokens,
            BigDecimal costUsd,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {

        static TaskResponse from(TaskView view) {
            return new TaskResponse(
                    view.id(),
                    view.issueId(),
                    view.agentId(),
                    view.agentName(),
                    view.runtime() == null
                            ? null
                            : new RuntimeSummaryResponse(
                                    view.runtime().id(),
                                    view.runtime().deviceName(),
                                    view.runtime().provider(),
                                    view.runtime().status().code()),
                    view.trigger().code(),
                    view.status().code(),
                    view.attempt(),
                    view.error(),
                    view.failureReason(),
                    view.inputTokens(),
                    view.outputTokens(),
                    view.costUsd(),
                    view.createdAt(),
                    view.startedAt(),
                    view.finishedAt());
        }
    }

    record MessageResponse(long seq, String type, String content, Instant createdAt) {

        static MessageResponse from(TaskMessageView view) {
            return new MessageResponse(view.seq(), view.type().code(), view.content(), view.createdAt());
        }
    }

    /** The runs of one issue, newest first. */
    @GetMapping
    List<TaskResponse> list(CurrentUser user, @PathVariable UUID workspaceId, @RequestParam UUID issueId) {
        return tasks.listForIssue(user.id(), workspaceId, issueId).stream().map(TaskResponse::from).toList();
    }

    /** The log of a task after line number {@code after} (default: from the start), at most 500 lines. */
    @GetMapping("/{taskId}/messages")
    List<MessageResponse> messages(
            CurrentUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "-1") long after) {
        return tasks.messages(user.id(), workspaceId, taskId, after).stream().map(MessageResponse::from).toList();
    }

    @PostMapping("/{taskId}/cancel")
    TaskResponse cancel(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        return TaskResponse.from(tasks.cancel(user.id(), workspaceId, taskId));
    }

    @PostMapping("/{taskId}/retry")
    ResponseEntity<TaskResponse> retry(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TaskResponse.from(tasks.retry(user.id(), workspaceId, taskId)));
    }
}
