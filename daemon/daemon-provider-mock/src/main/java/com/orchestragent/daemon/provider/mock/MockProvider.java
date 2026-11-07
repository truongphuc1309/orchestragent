package com.orchestragent.daemon.provider.mock;

import com.orchestragent.daemon.provider.AgentProvider;
import com.orchestragent.daemon.provider.CancellationToken;
import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.ProviderInfo;
import com.orchestragent.daemon.provider.RunListener;
import com.orchestragent.daemon.provider.RunRequest;
import com.orchestragent.daemon.provider.RunResult;
import com.orchestragent.daemon.provider.Usage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pretend AI tool for tests and demos: it never calls a real model. A run waits a little between steps, sends a
 * few log lines, writes a note into the work folder (so a resumed run can be seen to use the same folder) and
 * answers with a short text that names the issue.
 *
 * <p>What a run does can be chosen from the issue: the last of these markers in the prompt (title, description or
 * comments) wins, so a later comment can change the behaviour for the next run.
 * <ul>
 *   <li>{@code [mock:ok]} (the default): succeed.</li>
 *   <li>{@code [mock:fail]}: fail with reason {@code agent_error}.</li>
 *   <li>{@code [mock:hang]}: never finish, until cancelled or timed out.</li>
 *   <li>{@code [mock:crash]}: throw an exception, as a broken provider would.</li>
 * </ul>
 *
 * <p>The pause between steps is {@code ORCHESTRAGENT_MOCK_STEP_MS} milliseconds (default 1000).
 */
public final class MockProvider implements AgentProvider {

    public static final String ID = "mock";
    public static final String VERSION = "1.0.0";
    public static final String NOTES_FILE = "mock-notes.md";

    static final String STEP_ENV = "ORCHESTRAGENT_MOCK_STEP_MS";
    static final Duration DEFAULT_STEP = Duration.ofSeconds(1);

    /** The heading the daemon's prompt builder writes for the issue: {@code # Issue #12: Title}. */
    private static final Pattern ISSUE_HEADING = Pattern.compile("(?m)^#+\\s*Issue\\s+#\\d+:\\s*(.+?)\\s*$");
    private static final Pattern MODE_MARKER = Pattern.compile("\\[mock:([a-z]+)]", Pattern.CASE_INSENSITIVE);

    enum Mode {
        OK,
        FAIL,
        HANG,
        CRASH
    }

    private final Duration step;

    /** Used by {@link java.util.ServiceLoader}. */
    public MockProvider() {
        this(stepFromEnvironment(System.getenv(STEP_ENV)));
    }

    /** A provider that pauses {@code step} between steps (tests use a few milliseconds). */
    public MockProvider(Duration step) {
        this.step = step.isNegative() ? Duration.ZERO : step;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<ProviderInfo> detect() {
        return Optional.of(new ProviderInfo(VERSION, "built in"));
    }

    /** Only when asked for ({@code --providers mock}): a real daemon should not offer a pretend tool. */
    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public RunResult run(RunRequest request, RunListener listener, CancellationToken cancel) {
        Instant deadline = Instant.now().plus(request.timeout());
        String sessionId = request.priorSessionId() != null ? request.priorSessionId() : "mock-" + UUID.randomUUID();
        String title = titleOf(request.prompt());
        Mode mode = modeOf(request.prompt());

        listener.onMessage(MessageType.SYSTEM, "Mock provider " + VERSION + " started (session " + sessionId
                + (request.priorSessionId() != null ? ", resumed" : "") + ", model "
                + (request.model() == null ? "default" : request.model()) + ")");
        Optional<RunResult> stopped = pause(step, deadline, cancel, sessionId);
        if (stopped.isPresent()) {
            return stopped.get();
        }
        listener.onMessage(MessageType.TEXT, "Reading the issue: " + title);

        switch (mode) {
            case CRASH -> throw new IllegalStateException("Simulated crash of the mock provider ([mock:crash])");
            case HANG -> {
                listener.onMessage(MessageType.TEXT, "Thinking very hard (this run never finishes: [mock:hang])");
                while (true) {
                    stopped = pause(request.timeout(), deadline, cancel, sessionId);
                    if (stopped.isPresent()) {
                        return stopped.get();
                    }
                }
            }
            case FAIL -> {
                stopped = pause(step, deadline, cancel, sessionId);
                if (stopped.isPresent()) {
                    return stopped.get();
                }
                String error = "Simulated failure of the mock provider ([mock:fail])";
                listener.onMessage(MessageType.ERROR, error);
                return RunResult.failed(error, RunResult.AGENT_ERROR, sessionId, usage(request.prompt(), ""));
            }
            case OK -> {
                // Carry on below.
            }
        }

        stopped = pause(step, deadline, cancel, sessionId);
        if (stopped.isPresent()) {
            return stopped.get();
        }
        listener.onMessage(MessageType.TOOL_USE, "{\"tool\":\"write_file\",\"path\":\"" + NOTES_FILE + "\"}");
        int run = appendNote(request.workDir(), title, sessionId);
        listener.onMessage(MessageType.TOOL_RESULT, "Wrote " + NOTES_FILE + " (run " + run + " in this folder)");

        stopped = pause(step, deadline, cancel, sessionId);
        if (stopped.isPresent()) {
            return stopped.get();
        }
        String output = "Mock agent finished the issue \"" + title + "\".\n\n"
                + "This was a simulated run (run " + run + " in " + request.workDir().getFileName()
                + "); no AI model was called.";
        listener.onMessage(MessageType.TEXT, "Done.");
        return RunResult.succeeded(output, sessionId, usage(request.prompt(), output));
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    /** Waits, unless cancelled or past the deadline; then the run ends with the result returned. */
    private static Optional<RunResult> pause(Duration wanted, Instant deadline, CancellationToken cancel, String sessionId) {
        Duration left = Duration.between(Instant.now(), deadline);
        Duration wait = wanted.compareTo(left) < 0 ? wanted : left;
        try {
            if (cancel.await(wait.isNegative() ? Duration.ZERO : wait)) {
                return Optional.of(RunResult.failed("The run was cancelled", "cancelled", sessionId, Usage.NONE));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of(RunResult.failed("The run was interrupted", "cancelled", sessionId, Usage.NONE));
        }
        if (!Instant.now().isBefore(deadline)) {
            return Optional.of(RunResult.failed("The mock run timed out", RunResult.TIMEOUT, sessionId, Usage.NONE));
        }
        return Optional.empty();
    }

    static String titleOf(String prompt) {
        Matcher heading = ISSUE_HEADING.matcher(prompt);
        if (heading.find()) {
            return heading.group(1);
        }
        return prompt.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("(no title)");
    }

    static Mode modeOf(String prompt) {
        Matcher marker = MODE_MARKER.matcher(prompt);
        Mode mode = Mode.OK;
        while (marker.find()) {
            try {
                mode = Mode.valueOf(marker.group(1).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                // Not one of ours: keep the mode found before.
            }
        }
        return mode;
    }

    static Duration stepFromEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_STEP;
        }
        try {
            return Duration.ofMillis(Math.max(0, Long.parseLong(value.strip())));
        } catch (NumberFormatException e) {
            return DEFAULT_STEP;
        }
    }

    /** Appends a line to the notes file and returns how many runs it now records. */
    private static int appendNote(Path workDir, String title, String sessionId) {
        Path notes = workDir.resolve(NOTES_FILE);
        try {
            String line = "- " + Instant.now() + " session " + sessionId + ": " + title + System.lineSeparator();
            Files.writeString(notes, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try (var lines = Files.lines(notes, StandardCharsets.UTF_8)) {
                return (int) lines.filter(l -> l.startsWith("- ")).count();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + notes, e);
        }
    }

    private static Usage usage(String prompt, String output) {
        return new Usage((long) (prompt.length() / 4 + 1), (long) (output.length() / 4 + 1), BigDecimal.ZERO);
    }
}
