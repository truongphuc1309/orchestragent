package com.orchestragent.daemon.cli;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.cli.client.DaemonClient;
import com.orchestragent.daemon.cli.config.ConfigStore;
import com.orchestragent.daemon.cli.config.DaemonConfig;
import com.orchestragent.daemon.protocol.DaemonWorkspace;
import java.io.PrintWriter;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code login --server URL --token oat_...}: checks the token with the server, then saves it in
 * {@code config.json} (readable only by the user). {@code --token} without a value asks for it, so it does not end
 * up in the shell history.
 */
@Command(name = "login", mixinStandardHelpOptions = true, description = "Save the server address and an API token.")
final class LoginCommand implements Callable<Integer> {

    @Option(names = "--server", required = true, description = "Server URL, for example http://localhost:8080")
    String server;

    @Option(names = "--token", required = true, arity = "0..1", interactive = true,
            description = "API token (oat_...). Without a value, it is asked for without echo.")
    String token;

    @Option(names = "--no-verify", description = "Save without checking the token with the server.")
    boolean noVerify;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private final ConfigStore store;

    LoginCommand(ConfigStore store) {
        this.store = store;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        URI serverUri = ConfigStore.parseServer(server);
        String apiToken = ConfigStore.validToken(token);

        if (!noVerify) {
            List<DaemonWorkspace> workspaces;
            try {
                workspaces = new DaemonClient(serverUri, apiToken).workspaces();
            } catch (DaemonApiException e) {
                if (e.isUnauthorized()) {
                    throw new UserError("The server refused the token (wrong, revoked or expired); nothing was saved");
                }
                throw new UserError("Cannot check the token: " + e.getMessage() + "; nothing was saved (use --no-verify to save anyway)");
            }
            out.println("Token accepted. Workspaces: " + (workspaces.isEmpty()
                    ? "none yet (create or join one in the web app)"
                    : String.join(", ", workspaces.stream().map(DaemonWorkspace::name).toList())));
        }

        DaemonConfig saved = store.saveLogin(serverUri, apiToken);
        out.println("Saved to " + store.file() + " (daemon ID " + saved.daemonId() + ")");
        return 0;
    }
}
