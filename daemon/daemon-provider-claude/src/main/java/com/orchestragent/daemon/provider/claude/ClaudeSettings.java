package com.orchestragent.daemon.provider.claude;

import com.orchestragent.daemon.provider.ProviderSettings;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code "claude"} section of the daemon's {@code config.json}.
 *
 * <ul>
 *   <li>{@code command}: the Claude Code executable, a path or a name looked up on {@code PATH} (default
 *       {@code claude}).</li>
 *   <li>{@code permissionMode}: what Claude may do without asking (default {@code acceptEdits}: edit files in the
 *       task folder; commands need {@code allowedTools}). {@code bypassPermissions} lets it do anything; only when
 *       the user sets it, and the daemon warns.</li>
 *   <li>{@code allowedTools}, {@code disallowedTools}: lists passed to the CLI, for example
 *       {@code ["Bash(npm test:*)"]}. {@code AskUserQuestion} is always disallowed: nobody can answer it.</li>
 *   <li>{@code idleTimeoutMinutes}: stop a run that prints nothing for this long (default 10).</li>
 * </ul>
 */
record ClaudeSettings(
        Optional<String> command,
        String permissionMode,
        List<String> allowedTools,
        List<String> disallowedTools,
        Duration idleTimeout) {

    static final String DEFAULT_PERMISSION_MODE = "acceptEdits";
    static final String BYPASS = "bypassPermissions";
    static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(10);

    /** Claude Code cannot ask a person anything while the daemon runs it. */
    static final String ALWAYS_DISALLOWED = "AskUserQuestion";

    private static final Set<String> MODES = Set.of("acceptEdits", "auto", BYPASS, "default", "dontAsk", "manual", "plan");

    ClaudeSettings {
        command = command == null ? Optional.empty() : command;
        allowedTools = List.copyOf(allowedTools);
        disallowedTools = List.copyOf(disallowedTools);
    }

    static ClaudeSettings defaults() {
        return new ClaudeSettings(Optional.empty(), DEFAULT_PERMISSION_MODE, List.of(), List.of(), DEFAULT_IDLE_TIMEOUT);
    }

    static ClaudeSettings from(ProviderSettings settings) {
        String mode = settings.string("permissionMode").orElse(DEFAULT_PERMISSION_MODE);
        String known = MODES.stream().filter(m -> m.toLowerCase(Locale.ROOT).equals(mode.toLowerCase(Locale.ROOT))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Setting claude.permissionMode in config.json must be one of "
                        + String.join(", ", MODES.stream().sorted().toList()) + ", not " + mode));
        long idleMinutes = settings.number("idleTimeoutMinutes").orElse(DEFAULT_IDLE_TIMEOUT.toMinutes());
        if (idleMinutes < 1) {
            throw new IllegalArgumentException("Setting claude.idleTimeoutMinutes in config.json must be at least 1");
        }
        return new ClaudeSettings(
                settings.string("command"),
                known,
                settings.strings("allowedTools"),
                settings.strings("disallowedTools"),
                Duration.ofMinutes(idleMinutes));
    }

    boolean bypassesPermissions() {
        return BYPASS.equals(permissionMode);
    }
}
