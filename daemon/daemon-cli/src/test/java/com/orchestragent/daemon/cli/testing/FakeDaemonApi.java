package com.orchestragent.daemon.cli.testing;

import static com.orchestragent.daemon.cli.testing.FakeServer.json;
import static com.orchestragent.daemon.cli.testing.FakeServer.problem;
import static com.orchestragent.daemon.cli.testing.FakeServer.status;

import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.protocol.AgentInfo;
import com.orchestragent.daemon.protocol.ClaimRequest;
import com.orchestragent.daemon.protocol.ClaimResponse;
import com.orchestragent.daemon.protocol.ClaimedTask;
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
import com.orchestragent.daemon.protocol.TaskMessage;
import com.orchestragent.daemon.protocol.TaskMessagesRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A fake of the server's daemon API that keeps state the way the real one does: a claim hands out queued tasks
 * (dispatched), start makes them running, messages skip known numbers, complete and fail are safe to repeat, reports
 * for a cancelled task are ignored, and lines for a finished task are dropped. Only the token {@link #TOKEN} is
 * accepted.
 */
public final class FakeDaemonApi implements AutoCloseable {

    public static final String TOKEN = "oat_test_token";

    private static final Pattern TASK_PATH = Pattern.compile("/api/daemon/tasks/([0-9a-f-]+)/(\\w+)");

    public final FakeServer server;
    public final List<DaemonWorkspace> workspaces = new CopyOnWriteArrayList<>();
    public final List<RegisterRequest> registrations = new CopyOnWriteArrayList<>();
    public final List<List<UUID>> heartbeats = new CopyOnWriteArrayList<>();
    public final List<List<UUID>> deregistrations = new CopyOnWriteArrayList<>();
    public final List<ClaimRequest> claims = new CopyOnWriteArrayList<>();
    public final Deque<ClaimedTask> queue = new ConcurrentLinkedDeque<>();
    public final Map<UUID, String> statuses = new ConcurrentHashMap<>();
    public final Map<UUID, List<TaskMessage>> messages = new ConcurrentHashMap<>();
    public final Map<UUID, CompleteRequest> completions = new ConcurrentHashMap<>();
    public final Map<UUID, FailRequest> failures = new ConcurrentHashMap<>();
    /** Every call to complete, fail or start, in order: {@code start <id>}, {@code messages <id>}, ... */
    public final List<String> events = new CopyOnWriteArrayList<>();

    public FakeDaemonApi() throws IOException {
        server = new FakeServer();
        server.on("GET", "/api/daemon/workspaces", call -> authorized(call) ? json(workspaces) : unauthorized());
        server.on("POST", "/api/daemon/register", this::register);
        server.on("POST", "/api/daemon/heartbeat", call -> {
            RuntimeIdsRequest request = read(call, RuntimeIdsRequest.class);
            heartbeats.add(request.runtimeIds());
            return json(new HeartbeatResponse(request.runtimeIds()));
        });
        server.on("POST", "/api/daemon/deregister", call -> {
            deregistrations.add(read(call, RuntimeIdsRequest.class).runtimeIds());
            return new FakeServer.Reply(204, "");
        });
        server.on("POST", "/api/daemon/tasks/claim", this::claim);
        server.on("POST", "/api/daemon/tasks/[^/]+/\\w+", this::task);
        server.on("GET", "/api/daemon/tasks/[^/]+/status", this::task);
    }

    public URI uri() {
        return server.uri();
    }

    public DaemonClient client() {
        return new DaemonClient(uri(), TOKEN);
    }

    /** Puts a task in the queue (status queued). */
    public ClaimedTask enqueue(ClaimedTask task) {
        statuses.put(task.taskId(), "queued");
        queue.add(task);
        return task;
    }

    /** Puts a task straight into dispatched, as if this daemon had just claimed it. */
    public ClaimedTask dispatched(ClaimedTask task) {
        statuses.put(task.taskId(), "dispatched");
        return task;
    }

    public List<TaskMessage> messagesOf(UUID taskId) {
        return messages.getOrDefault(taskId, List.of());
    }

    public static ClaimedTask task(String provider, String title) {
        return task(provider, title, "assigned", null, null);
    }

    public static ClaimedTask task(String provider, String title, String trigger, String priorSessionId, String priorWorkDir) {
        return new ClaimedTask(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
                provider,
                trigger,
                1,
                new AgentInfo(UUID.randomUUID(), "Builder", "Write tidy code.", null),
                new IssueInfo(UUID.randomUUID(), 12, title, "Describe the project."),
                List.of(new CommentInfo("member", "Ana", "Please hurry", Instant.parse("2026-11-01T10:00:00Z"))),
                priorSessionId,
                priorWorkDir);
    }

    // ---- handlers ----------------------------------------------------------------------------------------

    private FakeServer.Reply register(FakeServer.Call call) {
        if (!authorized(call)) {
            return unauthorized();
        }
        RegisterRequest request = read(call, RegisterRequest.class);
        registrations.add(request);
        List<RegisteredRuntime> runtimes = new ArrayList<>();
        for (var provider : request.providers()) {
            // Stable per workspace and provider, as the server's (workspace, daemon, provider) key is.
            UUID id = UUID.nameUUIDFromBytes((request.workspaceId() + provider.provider()).getBytes());
            runtimes.add(new RegisteredRuntime(id, request.workspaceId(), provider.provider(), provider.version(),
                    request.deviceName(), "online"));
        }
        return json(new RegisterResponse(runtimes));
    }

    private FakeServer.Reply claim(FakeServer.Call call) {
        ClaimRequest request = read(call, ClaimRequest.class);
        claims.add(request);
        List<ClaimedTask> handed = new ArrayList<>();
        while (handed.size() < request.maxTasks()) {
            ClaimedTask next = queue.poll();
            if (next == null) {
                break;
            }
            statuses.put(next.taskId(), "dispatched");
            handed.add(next);
        }
        return json(new ClaimResponse(handed));
    }

    private FakeServer.Reply task(FakeServer.Call call) {
        if (!authorized(call)) {
            return unauthorized();
        }
        Matcher path = TASK_PATH.matcher(call.path());
        if (!path.matches()) {
            return problem(404, "task.not_found");
        }
        UUID id = UUID.fromString(path.group(1));
        String action = path.group(2);
        String current = statuses.get(id);
        if (current == null) {
            return problem(404, "task.not_found");
        }
        events.add(action + " " + id);
        synchronized (this) {
            switch (action) {
                case "status":
                    return status(statuses.get(id));
                case "start":
                    if (current.equals("cancelled") || current.equals("running")) {
                        return status(current);
                    }
                    if (current.equals("queued")) {
                        return problem(409, "task.lease_lost");
                    }
                    if (!current.equals("dispatched")) {
                        return problem(409, "task.invalid_state");
                    }
                    statuses.put(id, "running");
                    return status("running");
                case "messages":
                    if (current.equals("queued")) {
                        return problem(409, "task.lease_lost");
                    }
                    if (!isFinished(current)) {
                        List<TaskMessage> stored = messages.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>());
                        for (TaskMessage message : read(call, TaskMessagesRequest.class).messages()) {
                            if (stored.stream().noneMatch(known -> known.seq() == message.seq())) {
                                stored.add(message);
                            }
                        }
                    }
                    return status(current);
                case "complete":
                    if (current.equals("cancelled") || current.equals("completed")) {
                        return status(current);
                    }
                    if (!current.equals("running")) {
                        return problem(409, "task.invalid_state");
                    }
                    completions.put(id, read(call, CompleteRequest.class));
                    statuses.put(id, "completed");
                    return status("completed");
                case "fail":
                    if (current.equals("cancelled") || current.equals("failed")) {
                        return status(current);
                    }
                    if (!current.equals("running") && !current.equals("dispatched")) {
                        return problem(409, "task.invalid_state");
                    }
                    failures.put(id, read(call, FailRequest.class));
                    statuses.put(id, "failed");
                    return status("failed");
                default:
                    return problem(404, "not_found");
            }
        }
    }

    private static boolean isFinished(String status) {
        return status.equals("completed") || status.equals("failed") || status.equals("cancelled");
    }

    private static boolean authorized(FakeServer.Call call) {
        return ("Bearer " + TOKEN).equals(call.authorization());
    }

    private static FakeServer.Reply unauthorized() {
        return problem(401, "auth.unauthorized");
    }

    private static <T> T read(FakeServer.Call call, Class<T> type) {
        return DaemonClient.JSON.readValue(call.body(), type);
    }

    @Override
    public void close() {
        server.close();
    }
}
