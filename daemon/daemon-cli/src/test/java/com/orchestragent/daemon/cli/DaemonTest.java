package com.orchestragent.daemon.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.cli.client.Retry;
import com.orchestragent.daemon.cli.runner.PromptBuilder;
import com.orchestragent.daemon.cli.runner.TaskRunner;
import com.orchestragent.daemon.cli.runner.WorkDirs;
import com.orchestragent.daemon.cli.testing.FakeDaemonApi;
import com.orchestragent.daemon.protocol.ClaimedTask;
import com.orchestragent.daemon.protocol.DaemonWorkspace;
import com.orchestragent.daemon.protocol.ProviderInfo;
import com.orchestragent.daemon.protocol.RegisteredRuntime;
import com.orchestragent.daemon.provider.mock.MockProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonTest {

    @TempDir
    Path home;

    private final FakeDaemonApi api;
    private Daemon daemon;

    DaemonTest() throws Exception {
        api = new FakeDaemonApi();
        api.workspaces.add(new DaemonWorkspace(UUID.randomUUID(), "Acme", "acme"));
        api.workspaces.add(new DaemonWorkspace(UUID.randomUUID(), "Beta", "beta"));
    }

    @AfterEach
    void stop() {
        if (daemon != null) {
            daemon.stop();
        }
        api.close();
    }

    private Daemon daemon(DaemonClient client, int maxTasks, Duration step, Duration grace) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        Daemon.Settings settings = new Daemon.Settings(maxTasks, Duration.ofMillis(50), Duration.ofMillis(20), grace);
        TaskRunner.Timing timing = new TaskRunner.Timing(Duration.ofMillis(20), Duration.ofMillis(30), Duration.ofMinutes(1));
        Retry retry = new Retry(3, Duration.ofMillis(1), Duration.ofMillis(1), Retry.THREAD_SLEEP);
        daemon = new Daemon(client, "daemon-1", "laptop", List.of(new ProviderInfo("mock", MockProvider.VERSION)),
                settings, scheduler,
                sched -> new TaskRunner(client, Map.of("mock", new MockProvider(step)), new WorkDirs(home),
                        new PromptBuilder(), sched, timing, retry));
        return daemon;
    }

    @Test
    void registersInEveryWorkspaceAndKeepsTheRuntimesOnline() {
        List<RegisteredRuntime> runtimes = daemon(api.client(), 2, Duration.ofMillis(5), Duration.ofSeconds(5)).start();

        assertThat(runtimes).hasSize(2);
        assertThat(api.registrations).extracting(r -> r.workspaceId())
                .containsExactlyElementsOf(api.workspaces.stream().map(DaemonWorkspace::id).toList());
        assertThat(api.registrations).allSatisfy(r -> {
            assertThat(r.daemonId()).isEqualTo("daemon-1");
            assertThat(r.deviceName()).isEqualTo("laptop");
            assertThat(r.providers()).containsExactly(new ProviderInfo("mock", MockProvider.VERSION));
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> !api.heartbeats.isEmpty());
        assertThat(api.heartbeats.getFirst()).containsExactlyInAnyOrderElementsOf(runtimes.stream().map(RegisteredRuntime::id).toList());
    }

    @Test
    void claimsNoMoreThanItsFreeSlotsAndRunsTasksToTheEnd() {
        for (int i = 0; i < 3; i++) {
            api.enqueue(FakeDaemonApi.task("mock", "Task " + i));
        }

        daemon(api.client(), 2, Duration.ofMillis(100), Duration.ofSeconds(5)).start();

        await().atMost(Duration.ofSeconds(15)).until(() -> api.completions.size() == 3);
        assertThat(api.claims).allSatisfy(claim -> assertThat(claim.maxTasks()).isBetween(1, 2));
        assertThat(api.claims.getFirst().maxTasks()).isEqualTo(2);
        assertThat(api.completions.values()).allSatisfy(done -> assertThat(done.output()).contains("Task "));
    }

    @Test
    void stoppingWaitsForRunningTasksThenTakesTheRuntimesOffline() {
        ClaimedTask task = api.enqueue(FakeDaemonApi.task("mock", "Slow"));
        Daemon running = daemon(api.client(), 1, Duration.ofMillis(300), Duration.ofSeconds(10));
        running.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> "running".equals(api.statuses.get(task.taskId())));

        running.stop();

        assertThat(api.statuses.get(task.taskId())).isEqualTo("completed");
        assertThat(api.deregistrations).hasSize(1);
        assertThat(running.runningTasks()).isEmpty();
    }

    @Test
    void aTaskStillRunningAfterTheGracePeriodIsStoppedAndReported() {
        ClaimedTask task = api.enqueue(FakeDaemonApi.task("mock", "Forever [mock:hang]"));
        Daemon running = daemon(api.client(), 1, Duration.ofMillis(5), Duration.ofMillis(200));
        running.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> "running".equals(api.statuses.get(task.taskId())));

        running.stop();

        assertThat(api.failures.get(task.taskId()).failureReason()).isEqualTo("daemon_stopped");
        assertThat(api.deregistrations).hasSize(1);
    }

    @Test
    void aRefusedTokenStopsTheStart() {
        DaemonClient wrong = new DaemonClient(api.uri(), "oat_wrong");

        assertThatThrownBy(() -> daemon(wrong, 1, Duration.ofMillis(5), Duration.ofSeconds(1)).start())
                .isInstanceOf(UserError.class)
                .hasMessageContaining("refused the API token");
        assertThat(api.registrations).isEmpty();
    }

    @Test
    void noWorkspaceIsAUserError() {
        api.workspaces.clear();

        assertThatThrownBy(() -> daemon(api.client(), 1, Duration.ofMillis(5), Duration.ofSeconds(1)).start())
                .isInstanceOf(UserError.class)
                .hasMessageContaining("not a member of any workspace");
    }
}
