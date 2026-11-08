package com.orchestragent.agent.infrastructure.adapter.in.web;

import com.orchestragent.agent.application.DaemonService;
import com.orchestragent.agent.application.RuntimeService.ProviderInfo;
import com.orchestragent.agent.application.TaskExecutionService;
import com.orchestragent.agent.application.TaskExecutionService.CompletionInput;
import com.orchestragent.agent.application.TaskExecutionService.FailureInput;
import com.orchestragent.agent.application.TaskExecutionService.MessageInput;
import com.orchestragent.agent.application.TaskViews.ClaimedTask;
import com.orchestragent.agent.application.Views.RuntimeView;
import com.orchestragent.agent.domain.TaskStatus;
import com.orchestragent.daemon.protocol.AgentInfo;
import com.orchestragent.daemon.protocol.ClaimRequest;
import com.orchestragent.daemon.protocol.ClaimResponse;
import com.orchestragent.daemon.protocol.CommentInfo;
import com.orchestragent.daemon.protocol.CompleteRequest;
import com.orchestragent.daemon.protocol.DaemonWorkspace;
import com.orchestragent.daemon.protocol.FailRequest;
import com.orchestragent.daemon.protocol.HeartbeatResponse;
import com.orchestragent.daemon.protocol.IssueInfo;
import com.orchestragent.daemon.protocol.RegisterRequest;
import com.orchestragent.daemon.protocol.RegisterResponse;
import com.orchestragent.daemon.protocol.RegisteredRuntime;
import com.orchestragent.daemon.protocol.RuntimeIdsRequest;
import com.orchestragent.daemon.protocol.TaskMessagesRequest;
import com.orchestragent.daemon.protocol.TaskStatusResponse;
import com.orchestragent.platform.security.CurrentUser;
import com.orchestragent.shared.error.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API a daemon talks to, under {@code /api/daemon}. It is authenticated with API tokens (never cookies, see
 * the daemon security chain), and {@link CurrentUser} here is the owner of the token. Every call acts for
 * that user and only reaches what they own: a runtime or task of someone else is "not found".
 *
 * <p>The wire format lives in {@code daemon-protocol}, shared with the daemon. Its records carry no validation
 * annotations, so what a daemon may send is checked here and in the use cases.
 */
@RestController
@RequestMapping("/api/daemon")
class DaemonController {

    private final DaemonService daemon;
    private final TaskExecutionService execution;

    DaemonController(DaemonService daemon, TaskExecutionService execution) {
        this.daemon = daemon;
        this.execution = execution;
    }

    @GetMapping("/workspaces")
    List<DaemonWorkspace> workspaces(CurrentUser user) {
        return daemon.workspaces(user.id()).stream()
                .map(workspace -> new DaemonWorkspace(workspace.id(), workspace.name(), workspace.slug()))
                .toList();
    }

    @PostMapping("/register")
    RegisterResponse register(CurrentUser user, @RequestBody RegisterRequest request) {
        if (request.workspaceId() == null) {
            throw new ValidationException("daemon.invalid_request", "workspaceId is required");
        }
        List<ProviderInfo> providers = request.providers() == null
                ? List.of()
                : request.providers().stream()
                        .map(provider -> new ProviderInfo(provider.provider(), provider.version()))
                        .toList();
        List<RuntimeView> runtimes =
                daemon.register(user.id(), request.workspaceId(), request.daemonId(), request.deviceName(), providers);
        return new RegisterResponse(runtimes.stream().map(DaemonController::registered).toList());
    }

    @PostMapping("/heartbeat")
    HeartbeatResponse heartbeat(CurrentUser user, @RequestBody RuntimeIdsRequest request) {
        return new HeartbeatResponse(daemon.heartbeat(user.id(), ids(request)));
    }

    @PostMapping("/deregister")
    ResponseEntity<Void> deregister(CurrentUser user, @RequestBody RuntimeIdsRequest request) {
        daemon.deregister(user.id(), ids(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/claim")
    ClaimResponse claim(CurrentUser user, @RequestBody ClaimRequest request) {
        List<UUID> runtimeIds = request.runtimeIds() == null ? List.of() : request.runtimeIds();
        List<ClaimedTask> claimed = execution.claim(user.id(), runtimeIds, request.maxTasks());
        return new ClaimResponse(claimed.stream().map(DaemonController::claimed).toList());
    }

    @PostMapping("/tasks/{taskId}/start")
    TaskStatusResponse start(CurrentUser user, @PathVariable UUID taskId) {
        return statusOf(execution.start(user.id(), taskId));
    }

    @PostMapping("/tasks/{taskId}/messages")
    TaskStatusResponse messages(CurrentUser user, @PathVariable UUID taskId, @RequestBody TaskMessagesRequest request) {
        List<MessageInput> batch = request.messages() == null
                ? List.of()
                : request.messages().stream()
                        .map(message -> new MessageInput(message.seq(), message.type(), message.content()))
                        .toList();
        return statusOf(execution.appendMessages(user.id(), taskId, batch));
    }

    @GetMapping("/tasks/{taskId}/status")
    TaskStatusResponse status(CurrentUser user, @PathVariable UUID taskId) {
        return statusOf(execution.status(user.id(), taskId));
    }

    @PostMapping("/tasks/{taskId}/complete")
    TaskStatusResponse complete(CurrentUser user, @PathVariable UUID taskId, @RequestBody CompleteRequest request) {
        var usage = request.usage();
        CompletionInput input = new CompletionInput(
                request.output(),
                request.sessionId(),
                request.workDir(),
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.costUsd());
        return statusOf(execution.complete(user.id(), taskId, input));
    }

    @PostMapping("/tasks/{taskId}/fail")
    TaskStatusResponse fail(CurrentUser user, @PathVariable UUID taskId, @RequestBody FailRequest request) {
        FailureInput input = new FailureInput(request.error(), request.failureReason(), request.sessionId(), request.workDir());
        return statusOf(execution.fail(user.id(), taskId, input));
    }

    // ---- mapping -----------------------------------------------------------------------------------------

    private static List<UUID> ids(RuntimeIdsRequest request) {
        return request.runtimeIds() == null ? List.of() : request.runtimeIds();
    }

    private static TaskStatusResponse statusOf(TaskStatus status) {
        return new TaskStatusResponse(status.code());
    }

    private static RegisteredRuntime registered(RuntimeView view) {
        return new RegisteredRuntime(
                view.id(), view.workspaceId(), view.provider(), view.providerVersion(), view.deviceName(), view.status().code());
    }

    private static com.orchestragent.daemon.protocol.ClaimedTask claimed(ClaimedTask task) {
        return new com.orchestragent.daemon.protocol.ClaimedTask(
                task.taskId(),
                task.workspaceId(),
                task.runtimeId(),
                task.provider(),
                task.trigger().code(),
                task.attempt(),
                new AgentInfo(task.agent().id(), task.agent().name(), task.agent().instructions(), task.agent().model()),
                new IssueInfo(
                        task.issue().id(),
                        task.issue().key(),
                        task.issue().number(),
                        task.issue().title(),
                        task.issue().description()),
                task.comments().stream()
                        .map(comment -> new CommentInfo(comment.authorType(), comment.authorName(), comment.body(), comment.createdAt()))
                        .toList(),
                task.priorSessionId(),
                task.priorWorkDir());
    }
}
