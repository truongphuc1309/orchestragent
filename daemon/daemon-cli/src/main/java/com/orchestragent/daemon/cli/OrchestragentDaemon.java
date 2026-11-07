package com.orchestragent.daemon.cli;

import com.orchestragent.daemon.cli.client.DaemonApiException;
import com.orchestragent.daemon.cli.config.ConfigStore;
import com.orchestragent.daemon.provider.AgentProvider;
import java.io.PrintWriter;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.SimpleFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The {@code orchestragent-daemon} command: {@code login}, {@code doctor} and {@code start}. The daemon runs AI
 * agents on this computer for tasks of the Orchestragent server.
 */
@Command(
        name = "orchestragent-daemon",
        mixinStandardHelpOptions = true,
        versionProvider = OrchestragentDaemon.VersionProvider.class,
        description = "Runs Orchestragent agents on this computer.",
        subcommands = {LoginCommand.class, DoctorCommand.class, StartCommand.class})
public final class OrchestragentDaemon implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        configureLogging(false);
        System.exit(commandLine(ConfigStore::fromEnvironment, Providers::load).execute(args));
    }

    /**
     * The command line. The config store and the providers are chosen by the caller: tests use a temporary folder
     * and only the mock, so they never start a real AI tool found on the computer.
     */
    static CommandLine commandLine(Supplier<ConfigStore> store, Supplier<Map<String, AgentProvider>> providers) {
        CommandLine commandLine = new CommandLine(new OrchestragentDaemon(), new Factory(store, providers));
        commandLine.setExecutionExceptionHandler(OrchestragentDaemon::handle);
        return commandLine;
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** One line per message, without class names: {@code 12:01:02 INFO Task 0193...: completed}. */
    static void configureLogging(boolean verbose) {
        java.util.logging.Logger root = LogManager.getLogManager().getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(new SimpleFormatter() {
            @Override
            public String format(java.util.logging.LogRecord record) {
                String line = String.format("%1$tH:%1$tM:%1$tS %2$s %3$s%n",
                        record.getMillis(), record.getLevel().getName(), formatMessage(record));
                if (record.getThrown() != null) {
                    java.io.StringWriter trace = new java.io.StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(trace));
                    line += trace;
                }
                return line;
            }
        });
        Level level = verbose ? Level.FINE : Level.INFO;
        console.setLevel(level);
        root.setLevel(level);
        root.addHandler(console);
        // The JDK's HTTP client logs request details at FINE; keep it quiet even in verbose mode.
        java.util.logging.Logger.getLogger("jdk.internal.httpclient").setLevel(Level.WARNING);
    }

    /** Prints what the user can act on, without a stack trace. */
    private static int handle(Exception e, CommandLine commandLine, CommandLine.ParseResult parseResult) {
        PrintWriter err = commandLine.getErr();
        if (e instanceof UserError || e instanceof DaemonApiException) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
        err.println("Error: " + e);
        e.printStackTrace(err);
        return 2;
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"orchestragent-daemon " + Version.VALUE};
        }
    }

    /** Hands the config store to the subcommands. */
    private record Factory(Supplier<ConfigStore> store, Supplier<Map<String, AgentProvider>> providers) implements CommandLine.IFactory {
        @Override
        public <K> K create(Class<K> type) throws Exception {
            if (type == LoginCommand.class) {
                return type.cast(new LoginCommand(store.get()));
            }
            if (type == DoctorCommand.class) {
                return type.cast(new DoctorCommand(store.get(), providers));
            }
            if (type == StartCommand.class) {
                return type.cast(new StartCommand(store.get(), providers));
            }
            return CommandLine.defaultFactory().create(type);
        }
    }
}
