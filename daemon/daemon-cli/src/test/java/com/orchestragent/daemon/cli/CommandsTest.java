package com.orchestragent.daemon.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.orchestragent.daemon.cli.config.ConfigStore;
import com.orchestragent.daemon.cli.testing.FakeDaemonApi;
import com.orchestragent.daemon.protocol.DaemonWorkspace;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CommandsTest {

    @TempDir
    Path home;

    private final FakeDaemonApi api;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    CommandsTest() throws Exception {
        api = new FakeDaemonApi();
        api.workspaces.add(new DaemonWorkspace(UUID.randomUUID(), "Acme", "acme"));
    }

    @AfterEach
    void stop() {
        api.close();
    }

    private ConfigStore store() {
        return new ConfigStore(home);
    }

    private int run(String... args) {
        CommandLine commandLine = OrchestragentDaemon.commandLine(this::store);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    /** For {@code start}: if it wrongly gets going it would run forever, so fail after a while instead. */
    private int runWithin(String... args) {
        return assertTimeoutPreemptively(Duration.ofSeconds(20), () -> run(args));
    }

    @Test
    void loginChecksTheTokenAndSavesIt() {
        int code = run("login", "--server", api.uri().toString(), "--token", FakeDaemonApi.TOKEN);

        assertThat(code).isZero();
        assertThat(out.toString()).contains("Token accepted").contains("Acme").contains("Saved to");
        assertThat(out.toString()).doesNotContain(FakeDaemonApi.TOKEN);
        assertThat(store().load()).hasValueSatisfying(config -> assertThat(config.token()).isEqualTo(FakeDaemonApi.TOKEN));
    }

    @Test
    void loginWithARefusedTokenSavesNothing() {
        int code = run("login", "--server", api.uri().toString(), "--token", "oat_wrong");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("refused the token").doesNotContain("oat_wrong");
        assertThat(Files.exists(store().file())).isFalse();
    }

    @Test
    void loginRejectsSomethingThatIsNotAToken() {
        int code = run("login", "--server", api.uri().toString(), "--token", "hunter2", "--no-verify");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("oat_").doesNotContain("hunter2");
        assertThat(api.server.calls()).isEmpty();
    }

    @Test
    void doctorListsTheProvidersAndChecksTheServer() {
        run("login", "--server", api.uri().toString(), "--token", FakeDaemonApi.TOKEN);
        out.getBuffer().setLength(0);

        int code = run("doctor");

        assertThat(code).isZero();
        assertThat(out.toString())
                .contains("mock")
                .contains("found")
                .contains("only with --providers mock")
                .contains("token    saved (hidden)")
                .contains("reachable, token accepted, 1 workspace(s)")
                .doesNotContain(FakeDaemonApi.TOKEN);
    }

    @Test
    void doctorBeforeLoginSaysSo() {
        int code = run("doctor");

        assertThat(code).isEqualTo(1);
        assertThat(out.toString()).contains("not logged in");
    }

    @Test
    void startNeedsALoginFirst() {
        int code = run("start", "--providers", "mock");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("Not logged in");
    }

    @Test
    void startRefusesAnUnknownProvider() {
        run("login", "--server", api.uri().toString(), "--token", FakeDaemonApi.TOKEN);

        int code = runWithin("start", "--providers", "nope");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("Unknown provider nope").contains("mock");
        assertThat(api.registrations).isEmpty();
    }

    @Test
    void startWithoutProvidersDoesNotPickTheMock() {
        run("login", "--server", api.uri().toString(), "--token", FakeDaemonApi.TOKEN);

        int code = runWithin("start");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("--providers mock");
        assertThat(api.registrations).isEmpty();
    }
}
