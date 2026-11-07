package com.orchestragent.daemon.cli.client;

import static com.orchestragent.daemon.cli.testing.FakeServer.json;
import static com.orchestragent.daemon.cli.testing.FakeServer.problem;
import static com.orchestragent.daemon.cli.testing.FakeServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.daemon.cli.testing.FakeServer;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.TaskMessage;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DaemonClientTest {

    private final FakeServer server;
    private final DaemonClient client;

    DaemonClientTest() throws Exception {
        server = new FakeServer();
        client = new DaemonClient(URI.create(server.uri() + "/"), "oat_secret");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void sendsTheTokenAsABearerAndNamesItself() {
        server.on("GET", "/api/daemon/workspaces", call -> new FakeServer.Reply(200,
                "[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Acme\",\"slug\":\"acme\",\"extra\":true}]"));

        var workspaces = client.workspaces();

        assertThat(workspaces).singleElement().satisfies(workspace -> assertThat(workspace.name()).isEqualTo("Acme"));
        FakeServer.Call call = server.calls().getFirst();
        assertThat(call.authorization()).isEqualTo("Bearer oat_secret");
        assertThat(call.userAgent()).startsWith("orchestragent-daemon/");
    }

    @Test
    void readsAClaimedTaskWithItsComments() {
        UUID taskId = UUID.randomUUID();
        server.on("POST", "/api/daemon/tasks/claim", call -> new FakeServer.Reply(200, """
                {"tasks":[{"taskId":"%s","workspaceId":"%s","runtimeId":"%s","provider":"mock","trigger":"comment",
                  "attempt":2,"agent":{"id":"%s","name":"Bot","instructions":"Be nice","model":null},
                  "issue":{"id":"%s","number":5,"title":"T","description":null},
                  "comments":[{"authorType":"member","authorName":"Ana","body":"Hi","createdAt":"2026-11-01T10:00:00.123456Z"}],
                  "priorSessionId":"s-1","priorWorkDir":null}]}
                """.formatted(taskId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));

        List<ClaimedTask> tasks = client.claim(List.of(UUID.randomUUID()), 2);

        assertThat(tasks).singleElement().satisfies(task -> {
            assertThat(task.taskId()).isEqualTo(taskId);
            assertThat(task.attempt()).isEqualTo(2);
            assertThat(task.comments().getFirst().createdAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00.123456Z"));
            assertThat(task.priorSessionId()).isEqualTo("s-1");
        });
        assertThat(server.calls().getFirst().body()).contains("\"maxTasks\":2");
    }

    @Test
    void sendsMessagesAsABatchAndReturnsTheStatus() {
        UUID taskId = UUID.randomUUID();
        server.on("POST", "/api/daemon/tasks/" + taskId + "/messages", call -> status("cancelled"));

        String result = client.messages(taskId, List.of(new TaskMessage(0, "text", "hello")));

        assertThat(result).isEqualTo("cancelled");
        assertThat(server.calls().getFirst().body())
                .isEqualTo("{\"messages\":[{\"seq\":0,\"type\":\"text\",\"content\":\"hello\"}]}");
    }

    @Test
    void turnsAProblemDetailIntoAnExceptionWithItsCode() {
        UUID taskId = UUID.randomUUID();
        server.on("POST", "/api/daemon/tasks/" + taskId + "/start", call -> problem(409, "task.lease_lost"));

        assertThatThrownBy(() -> client.start(taskId))
                .isInstanceOfSatisfying(DaemonApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(409);
                    assertThat(e.code()).isEqualTo("task.lease_lost");
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("Detail of task.lease_lost").doesNotContain("oat_secret");
                });
    }

    @Test
    void aServerErrorIsRetryable() {
        server.on("POST", "/api/daemon/heartbeat", call -> new FakeServer.Reply(503, "<html>down</html>"));

        assertThatThrownBy(() -> client.heartbeat(List.of()))
                .isInstanceOfSatisfying(DaemonApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(503);
                    assertThat(e.code()).isNull();
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void anUnreachableServerIsStatusZeroAndRetryable() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        DaemonClient nowhere = new DaemonClient(URI.create("http://127.0.0.1:" + port), "oat_secret");

        assertThatThrownBy(nowhere::workspaces)
                .isInstanceOfSatisfying(DaemonApiException.class, e -> {
                    assertThat(e.status()).isZero();
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void deregisterAcceptsAnEmptyAnswer() {
        server.on("POST", "/api/daemon/deregister", call -> new FakeServer.Reply(204, ""));

        client.deregister(List.of(UUID.randomUUID()));

        assertThat(server.unhandled()).isEmpty();
        assertThat(json(List.of()).status()).isEqualTo(200);
    }
}
