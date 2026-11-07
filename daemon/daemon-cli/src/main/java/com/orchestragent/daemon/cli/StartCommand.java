package com.orchestragent.daemon.cli;

import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.cli.client.Retry;
import com.orchestragent.daemon.cli.config.ConfigStore;
import com.orchestragent.daemon.cli.config.DaemonConfig;
import com.orchestragent.daemon.cli.runner.PromptBuilder;
import com.orchestragent.daemon.cli.runner.TaskRunner;
import com.orchestragent.daemon.cli.runner.WorkDirs;
import com.orchestragent.daemon.protocol.ProviderInfo;
import com.orchestragent.daemon.provider.AgentProvider;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code start}: runs the daemon in the foreground until Ctrl+C. */
@Command(name = "start", mixinStandardHelpOptions = true, description = "Run the daemon in the foreground (Ctrl+C stops it).")
final class StartCommand implements Callable<Integer> {

    @Option(names = "--providers", split = ",", paramLabel = "ID",
            description = "Providers to run, for example claude,mock. Default: every installed provider except mock.")
    List<String> providerNames;

    @Option(names = "--max-tasks", defaultValue = "2", description = "Tasks run at the same time (1-10, default ${DEFAULT-VALUE}).")
    int maxTasks;

    @Option(names = "--task-timeout", defaultValue = "30", paramLabel = "MINUTES",
            description = "Longest a run may take, in minutes (default ${DEFAULT-VALUE}).")
    long taskTimeoutMinutes;

    @Option(names = "--device-name", description = "Name of this computer as shown in the web app. Default: the host name.")
    String deviceName;

    @Option(names = {"-v", "--verbose"}, description = "Log more.")
    boolean verbose;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private final ConfigStore store;

    StartCommand(ConfigStore store) {
        this.store = store;
    }

    @Override
    public Integer call() throws InterruptedException {
        OrchestragentDaemon.configureLogging(verbose);
        PrintWriter out = spec.commandLine().getOut();
        if (taskTimeoutMinutes < 1) {
            throw new UserError("--task-timeout must be at least 1 minute");
        }
        Daemon.Settings settings = Daemon.Settings.defaults(maxTasks);
        DaemonConfig config = store.load()
                .orElseThrow(() -> new UserError("Not logged in; run login --server URL --token oat_... first"));

        Map<String, AgentProvider> selected = new LinkedHashMap<>();
        List<ProviderInfo> registered = Providers.select(Providers.load(), providerNames).stream()
                .peek(found -> selected.put(found.provider().id(), found.provider()))
                .map(found -> new ProviderInfo(found.provider().id(), found.info().orElseThrow().version()))
                .toList();

        DaemonClient client = new DaemonClient(config.server(), config.token());
        WorkDirs workDirs = new WorkDirs(store.workspacesRoot());
        TaskRunner.Timing timing = new TaskRunner.Timing(
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(taskTimeoutMinutes));
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(4, Thread.ofPlatform().name("daemon-", 1).daemon().factory());
        Daemon daemon = new Daemon(client, config.daemonId(), deviceName(), registered, settings, scheduler,
                sched -> new TaskRunner(client, selected, workDirs, new PromptBuilder(), sched, timing, Retry.forReports()));

        try {
            daemon.start();
        } catch (RuntimeException e) {
            scheduler.shutdownNow();
            throw e;
        }
        out.println("Connected to " + config.server() + " with " + String.join(", ", selected.keySet())
                + ". Work folders: " + workDirs.root() + ". Press Ctrl+C to stop.");
        out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(daemon::stop, "shutdown"));
        daemon.awaitStopped();
        return 0;
    }

    private String deviceName() {
        if (deviceName != null && !deviceName.isBlank()) {
            return deviceName.strip();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String fromEnv = System.getenv("COMPUTERNAME") != null ? System.getenv("COMPUTERNAME") : System.getenv("HOSTNAME");
            return fromEnv == null || fromEnv.isBlank() ? "unknown computer" : fromEnv;
        }
    }
}
