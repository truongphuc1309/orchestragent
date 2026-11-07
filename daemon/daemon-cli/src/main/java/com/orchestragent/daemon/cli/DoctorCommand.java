package com.orchestragent.daemon.cli;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.cli.config.ConfigStore;
import com.orchestragent.daemon.cli.config.DaemonConfig;
import com.orchestragent.daemon.provider.AgentProvider;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * {@code doctor}: lists the providers and whether each tool is installed, shows the saved login (never the token)
 * and checks that the server accepts it. Exits with 1 when something needs fixing.
 */
@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Check the providers, the login and the server.")
final class DoctorCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private final ConfigStore store;
    private final Supplier<Map<String, AgentProvider>> providers;

    DoctorCommand(ConfigStore store, Supplier<Map<String, AgentProvider>> providers) {
        this.store = store;
        this.providers = providers;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        boolean healthy = true;
        Map<String, AgentProvider> available = Providers.configure(providers.get(), store);

        out.println("Providers:");
        boolean anyInstalled = false;
        for (Providers.Found found : Providers.detectAll(available)) {
            String id = found.provider().id();
            if (found.info().isPresent()) {
                anyInstalled = true;
                String version = found.info().get().version();
                String location = found.info().get().location();
                out.printf("  %-8s found  %s%s%s%n", id, version.isEmpty() ? "(version unknown)" : version,
                        location.isEmpty() ? "" : "  " + location,
                        found.provider().enabledByDefault() ? "" : "  (only with --providers " + id + ")");
            } else {
                out.printf("  %-8s not found%n", id);
            }
        }
        if (available.isEmpty()) {
            out.println("  (none on the class path)");
        }
        healthy &= anyInstalled;

        out.println("Config: " + store.file());
        Optional<DaemonConfig> config;
        try {
            config = store.load();
        } catch (UserError e) {
            out.println("  " + e.getMessage());
            return 1;
        }
        if (config.isEmpty()) {
            out.println("  not logged in (run login --server URL --token oat_...)");
            return 1;
        }
        out.println("  server   " + config.get().server());
        out.println("  daemonId " + config.get().daemonId());
        out.println("  token    saved (hidden)");

        try {
            int count = new DaemonClient(config.get().server(), config.get().token()).workspaces().size();
            out.println("Server: reachable, token accepted, " + count + " workspace(s)");
        } catch (DaemonApiException e) {
            out.println("Server: " + (e.isUnauthorized() ? "the token was refused; run login again" : e.getMessage()));
            healthy = false;
        }
        return healthy ? 0 : 1;
    }
}
