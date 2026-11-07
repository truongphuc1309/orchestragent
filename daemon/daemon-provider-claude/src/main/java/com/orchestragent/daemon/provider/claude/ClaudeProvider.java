package com.orchestragent.daemon.provider.claude;

import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.ProviderInfo;
import com.orchestragent.daemon.provider.ProviderSettings;
import com.orchestragent.daemon.provider.RunListener;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import com.orchestragent.daemon.provider.Usage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs Claude Code: {@code claude -p --output-format stream-json --verbose --permission-mode <mode> [--model M]
 * [--resume SID] ...} in the task folder, with the prompt on standard input.
 *
 * <p>Standard output is read line by line ({@link StreamJsonParser}) and standard error is kept for error messages,
 * each on its own thread. A run stops when the CLI exits, when it is cancelled, when it takes longer than the
 * request's timeout, or when it prints nothing for {@link ClaudeSettings#idleTimeout()}; stopping kills the whole
 * process tree (children first). When the CLI refuses to resume the earlier session (it no longer exists), the run
 * starts once more without {@code --resume}.
 */
public final class ClaudeProvider implements AgentProvider {

    public static final String ID = "claude";

    static final Duration DETECT_TIMEOUT = Duration.ofSeconds(10);
    static final String RESUME_REJECTED = "no conversation found";

    /** Variables that mark a process as running inside Claude Code; a child that inherits them gets confused. */
    private static final List<String> INHERITED_MARKERS = List.of(
            "CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "CLAUDE_CODE_EXECPATH", "CLAUDE_CODE_SESSION_ID", "CLAUDE_CODE_SSE_PORT");

    private static final Logger LOG = System.getLogger(ClaudeProvider.class.getName());
    private static final Pattern VERSION = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\S*)");
    private static final int STDERR_LINES = 40;
    private static final Duration SCAN_INTERVAL = Duration.ofSeconds(1);
    private static final Duration READER_GRACE = Duration.ofSeconds(5);

    private final Executables executables;
    private final Executables.Launcher fixedLauncher;
    private volatile ClaudeSettings settings = ClaudeSettings.defaults();

    /** Used by {@link java.util.ServiceLoader}. */
    public ClaudeProvider() {
        this(Executables.fromEnvironment(), null);
    }

    /** For tests: {@code fixedCommand} replaces the lookup of the executable (a fake CLI). */
    ClaudeProvider(Executables executables, List<String> fixedCommand) {
        this.executables = executables;
        this.fixedLauncher = fixedCommand == null ? null : Executables.Launcher.of(fixedCommand);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void configure(ProviderSettings providerSettings) {
        ClaudeSettings parsed = ClaudeSettings.from(providerSettings);
        if (parsed.bypassesPermissions()) {
            LOG.log(Level.WARNING, "claude.permissionMode is bypassPermissions: agents may run any command and change any"
                    + " file this account can reach, without asking. Use it only on a machine you can afford to lose.");
        }
        settings = parsed;
    }

    ClaudeSettings settings() {
        return settings;
    }

    /** For tests: settings users cannot write, such as an idle timeout of seconds. */
    void use(ClaudeSettings newSettings) {
        settings = newSettings;
    }

    @Override
    public Optional<ProviderInfo> detect() {
        try {
            Optional<Executables.Launcher> launcher = launcher();
            if (launcher.isEmpty()) {
                return Optional.empty();
            }
            ProcessBuilder builder = new ProcessBuilder(launcher.get().command(List.of("--version"))).redirectErrorStream(true);
            cleanEnvironment(builder.environment());
            Process process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(DETECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                killTree(process, Set.of());
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            Matcher version = VERSION.matcher(output);
            return Optional.of(new ProviderInfo(version.find() ? version.group(1) : "", launcher.get().location()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Override
    public RunResult run(RunRequest request, RunListener listener, CancellationToken cancel) {
        Optional<Executables.Launcher> launcher = launcher();
        if (launcher.isEmpty()) {
            return RunResult.failed("Claude Code was not found on this computer (see the daemon's doctor command)",
                    "provider_unavailable", null, Usage.NONE);
        }
        ClaudeSettings current = settings;
        Instant deadline = Instant.now().plus(request.timeout());

        Attempt attempt = attempt(launcher.get(), current, request, request.priorSessionId(), deadline, listener, cancel);
        if (request.priorSessionId() != null && attempt.resumeRejected(request.priorSessionId())) {
            listener.onMessage(MessageType.SYSTEM, "The earlier session " + request.priorSessionId()
                    + " could not be resumed; starting a new session");
            attempt = attempt(launcher.get(), current, request, null, deadline, listener, cancel);
        }
        return attempt.toResult(current, request.timeout());
    }

    // ---- one run of the CLI ------------------------------------------------------------------------------

    enum Stop {
        NONE,
        CANCELLED,
        TIMEOUT,
        IDLE,
        START_FAILED
    }

    /** How one start of the CLI ended. */
    record Attempt(StreamJsonParser parser, Stop stop, int exitCode, String stderr, String startError) {

        boolean resumeRejected(String requested) {
            if (stop != Stop.NONE) {
                return false;
            }
            StreamJsonParser.Result result = parser.result();
            if (result != null && !result.error()) {
                return false;
            }
            String said = (stderr + "\n" + (result == null ? "" : result.output())).toLowerCase(Locale.ROOT);
            if (said.contains(RESUME_REJECTED)) {
                return true;
            }
            // The CLI answered with another session than the one asked for: the earlier one did not load.
            return parser.sessionId() != null && !parser.sessionId().equals(requested);
        }

        RunResult toResult(ClaudeSettings settings, Duration timeout) {
            String sessionId = parser.sessionId();
            StreamJsonParser.Result result = parser.result();
            return switch (stop) {
                case START_FAILED -> RunResult.failed("Could not start Claude Code: " + startError, "provider_unavailable",
                        null, Usage.NONE);
                case CANCELLED -> RunResult.failed("The run was cancelled", "cancelled", sessionId, Usage.NONE);
                case TIMEOUT -> RunResult.failed("Claude Code did not finish within " + minutes(timeout) + " and was stopped",
                        RunResult.TIMEOUT, sessionId, Usage.NONE);
                case IDLE -> RunResult.failed("Claude Code printed nothing for " + minutes(settings.idleTimeout())
                        + " and was stopped", "idle_timeout", sessionId, Usage.NONE);
                case NONE -> {
                    if (result != null && !result.error()) {
                        yield RunResult.succeeded(result.output(), sessionId, result.usage());
                    }
                    if (result != null) {
                        yield RunResult.failed(result.output().isEmpty() ? "Claude Code reported an error" : result.output(),
                                RunResult.AGENT_ERROR, sessionId, result.usage());
                    }
                    yield RunResult.failed("Claude Code exited with code " + exitCode + " without a result"
                            + (stderr.isBlank() ? "" : ": " + stderr.strip()), RunResult.AGENT_ERROR, sessionId, Usage.NONE);
                }
            };
        }

        private static String minutes(Duration duration) {
            long minutes = duration.toMinutes();
            return minutes >= 1 ? minutes + " min" : duration.toSeconds() + " s";
        }
    }

    private Attempt attempt(
            Executables.Launcher launcher,
            ClaudeSettings current,
            RunRequest request,
            String resume,
            Instant deadline,
            RunListener listener,
            CancellationToken cancel) {
        StreamJsonParser parser = new StreamJsonParser(listener, request.workDir());
        if (cancel.isCancelled()) {
            return new Attempt(parser, Stop.CANCELLED, -1, "", null);
        }
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(launcher.command(arguments(current, request.model(), resume)))
                    .directory(request.workDir().toFile());
            cleanEnvironment(builder.environment());
            process = builder.start();
        } catch (IOException | IllegalArgumentException e) {
            return new Attempt(parser, Stop.START_FAILED, -1, "", e.getMessage());
        }

        AtomicLong lastActivity = new AtomicLong(System.nanoTime());
        Deque<String> stderr = new ArrayDeque<>();
        Thread out = reader("claude-stdout", process.getInputStream(), line -> {
            lastActivity.set(System.nanoTime());
            parser.accept(line);
        });
        Thread err = reader("claude-stderr", process.getErrorStream(), line -> {
            lastActivity.set(System.nanoTime());
            synchronized (stderr) {
                stderr.addLast(line);
                if (stderr.size() > STDERR_LINES) {
                    stderr.removeFirst();
                }
            }
        });
        Thread.ofPlatform().daemon().name("claude-stdin").start(() -> writePrompt(process, request.prompt()));
        // Everything the CLI starts, as seen while it runs: what is still alive at the end is killed with it.
        Set<ProcessHandle> started = ConcurrentHashMap.newKeySet();
        cancel.onCancel(() -> killTree(process, started));

        Stop stop = Stop.NONE;
        long idleNanos = current.idleTimeout().toNanos();
        long nextScan = 0;
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() - nextScan > 0) {
                    process.descendants().forEach(started::add);
                    nextScan = System.nanoTime() + SCAN_INTERVAL.toNanos();
                }
                if (cancel.isCancelled()) {
                    stop = Stop.CANCELLED;
                } else if (!Instant.now().isBefore(deadline)) {
                    stop = Stop.TIMEOUT;
                } else if (System.nanoTime() - lastActivity.get() > idleNanos) {
                    stop = Stop.IDLE;
                }
                if (stop != Stop.NONE) {
                    break;
                }
            }
            // Stopped or not: nothing the run started may outlive it (a dev server left in the background, say).
            killTree(process, started);
            process.waitFor(10, TimeUnit.SECONDS);
            out.join(READER_GRACE);
            err.join(READER_GRACE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killTree(process, started);
            stop = Stop.CANCELLED;
        }
        if (out.isAlive() || err.isAlive()) {
            // Something we could not kill still holds the output open. Never wait for it: closing a pipe that
            // another thread reads can block on Windows, so close in the background and stop listening.
            parser.stop();
            Thread.ofPlatform().daemon().name("claude-close").start(() -> {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
            });
            LOG.log(Level.WARNING, "A process started by Claude Code still holds its output open; it was left behind");
        }
        if (cancel.isCancelled() && stop == Stop.NONE && parser.result() == null) {
            stop = Stop.CANCELLED;
        }
        int exitCode = process.isAlive() ? -1 : process.exitValue();
        String errorText;
        synchronized (stderr) {
            errorText = String.join("\n", stderr);
        }
        return new Attempt(parser, stop, exitCode, errorText, null);
    }

    /** The arguments. The prompt goes to standard input, so no argument ever holds issue text. */
    static List<String> arguments(ClaudeSettings settings, String model, String resume) {
        List<String> args = new ArrayList<>();
        args.addAll(List.of("-p", "--output-format", "stream-json", "--verbose", "--permission-mode", settings.permissionMode()));
        if (model != null) {
            args.addAll(List.of("--model", model));
        }
        if (resume != null) {
            args.addAll(List.of("--resume", resume));
        }
        if (!settings.allowedTools().isEmpty()) {
            args.add("--allowedTools");
            args.addAll(settings.allowedTools());
        }
        args.add("--disallowedTools");
        args.add(ClaudeSettings.ALWAYS_DISALLOWED);
        settings.disallowedTools().stream().filter(tool -> !tool.equals(ClaudeSettings.ALWAYS_DISALLOWED)).forEach(args::add);
        return args;
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private Optional<Executables.Launcher> launcher() {
        if (fixedLauncher != null) {
            return Optional.of(fixedLauncher);
        }
        return executables.find(settings.command().orElse("claude")).map(executables::launcherFor);
    }

    static void cleanEnvironment(Map<String, String> environment) {
        environment.keySet().removeIf(key -> INHERITED_MARKERS.contains(key.toUpperCase(Locale.ROOT))
                || key.toUpperCase(Locale.ROOT).startsWith("CLAUDECODE_"));
    }

    /**
     * Kills everything the process started (the ones seen earlier, and its descendants now, a few times over, as
     * a dying child may still start one), then the process itself.
     */
    static void killTree(Process process, Set<ProcessHandle> seen) {
        seen.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        for (int round = 0; round < 3; round++) {
            List<ProcessHandle> descendants;
            try {
                descendants = process.descendants().filter(ProcessHandle::isAlive).toList();
            } catch (RuntimeException e) {
                break;
            }
            if (descendants.isEmpty()) {
                break;
            }
            descendants.forEach(ProcessHandle::destroyForcibly);
        }
        process.destroyForcibly();
    }

    private static Thread reader(String name, InputStream stream, Consumer<String> onLine) {
        return Thread.ofPlatform().daemon().name(name).start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            } catch (IOException e) {
                // The stream closed: the process ended or was killed.
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Reading Claude Code output failed", e);
            }
        });
    }

    private static void writePrompt(Process process, String prompt) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // The CLI exited before reading everything; its output says why.
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing more to do.
        }
    }
}
