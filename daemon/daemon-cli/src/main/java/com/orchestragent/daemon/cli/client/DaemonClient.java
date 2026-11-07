package com.orchestragent.daemon.cli.client;

import com.orchestragent.daemon.cli.Version;
import com.orchestragent.daemon.protocol.ClaimRequest;
import com.orchestragent.daemon.protocol.ClaimResponse;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.CompleteRequest;
import com.orchestragent.daemon.protocol.DaemonWorkspace;
import com.orchestragent.daemon.protocol.FailRequest;
import com.orchestragent.daemon.protocol.HeartbeatResponse;
import com.orchestragent.daemon.protocol.RegisterRequest;
import com.orchestragent.daemon.protocol.RegisterResponse;
import com.orchestragent.daemon.protocol.RuntimeIdsRequest;
import com.orchestragent.daemon.protocol.TaskMessage;
import com.orchestragent.daemon.protocol.TaskMessagesRequest;
import com.orchestragent.daemon.protocol.TaskStatusResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The daemon API of the server ({@code /api/daemon/**}), over {@link HttpClient}, with the wire records of
 * {@code daemon-protocol}. Every call sends the API token as {@code Authorization: Bearer}; the token is never
 * logged or put in an error message. A failed call throws {@link DaemonApiException}. Thread-safe.
 */
public final class DaemonClient {

    public static final JsonMapper JSON = JsonMapper.builder()
            // A newer server may add fields; an older daemon just ignores them.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final URI base;
    private final String token;
    private final HttpClient http;

    public DaemonClient(URI server, String token) {
        this(server, token, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    DaemonClient(URI server, String token, HttpClient http) {
        String text = Objects.requireNonNull(server, "server").toString();
        this.base = URI.create(text.endsWith("/") ? text : text + "/");
        this.token = Objects.requireNonNull(token, "token");
        this.http = http;
    }

    public URI server() {
        return base;
    }

    // ---- workspaces and runtimes -------------------------------------------------------------------------

    public List<DaemonWorkspace> workspaces() {
        return send("GET", "api/daemon/workspaces", null, new TypeReference<List<DaemonWorkspace>>() {});
    }

    public RegisterResponse register(RegisterRequest request) {
        return send("POST", "api/daemon/register", request, new TypeReference<RegisterResponse>() {});
    }

    public HeartbeatResponse heartbeat(List<UUID> runtimeIds) {
        return send("POST", "api/daemon/heartbeat", new RuntimeIdsRequest(runtimeIds), new TypeReference<HeartbeatResponse>() {});
    }

    public void deregister(List<UUID> runtimeIds) {
        send("POST", "api/daemon/deregister", new RuntimeIdsRequest(runtimeIds), null);
    }

    // ---- tasks -------------------------------------------------------------------------------------------

    public List<ClaimedTask> claim(List<UUID> runtimeIds, int maxTasks) {
        ClaimResponse response = send(
                "POST", "api/daemon/tasks/claim", new ClaimRequest(runtimeIds, maxTasks), new TypeReference<ClaimResponse>() {});
        return response.tasks() == null ? List.of() : response.tasks();
    }

    /** {@code dispatched → running}. Returns the task's status, {@code cancelled} if someone cancelled it meanwhile. */
    public String start(UUID taskId) {
        return status("POST", "api/daemon/tasks/" + taskId + "/start", null);
    }

    public String messages(UUID taskId, List<TaskMessage> messages) {
        return status("POST", "api/daemon/tasks/" + taskId + "/messages", new TaskMessagesRequest(messages));
    }

    public String status(UUID taskId) {
        return status("GET", "api/daemon/tasks/" + taskId + "/status", null);
    }

    public String complete(UUID taskId, CompleteRequest request) {
        return status("POST", "api/daemon/tasks/" + taskId + "/complete", request);
    }

    public String fail(UUID taskId, FailRequest request) {
        return status("POST", "api/daemon/tasks/" + taskId + "/fail", request);
    }

    // ---- HTTP --------------------------------------------------------------------------------------------

    private String status(String method, String path, Object body) {
        TaskStatusResponse response = send(method, path, body, new TypeReference<TaskStatusResponse>() {});
        return response.status();
    }

    private <T> T send(String method, String path, Object body, TypeReference<T> type) {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("User-Agent", "orchestragent-daemon/" + Version.VALUE);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DaemonApiException(0, null, "Cannot reach the server at " + base + ": " + describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DaemonApiException(0, null, "Interrupted while calling the server", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw problem(method, path, status, response.body());
        }
        if (type == null || response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(response.body(), type);
        } catch (JacksonException e) {
            throw new DaemonApiException(status, null, "Unexpected answer from " + method + " /" + path + ": " + e.getOriginalMessage(), e);
        }
    }

    /** Turns an error answer (an RFC 7807 problem detail, usually) into an exception with its code and detail. */
    private static DaemonApiException problem(String method, String path, int status, String body) {
        String code = null;
        String detail = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode problem = JSON.readTree(body);
                code = text(problem, "code");
                detail = text(problem, "detail");
                if (detail == null) {
                    detail = text(problem, "title");
                }
            } catch (JacksonException notJson) {
                // Not a problem detail (a proxy's error page, say): the status alone has to do.
            }
        }
        String message = method + " /" + path + " failed with HTTP " + status
                + (code == null ? "" : " (" + code + ")")
                + (detail == null ? "" : ": " + detail);
        return new DaemonApiException(status, code, message);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String describe(IOException e) {
        if (e instanceof java.net.ConnectException) {
            return "connection refused (is the server running at this address?)";
        }
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "no answer in time";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
