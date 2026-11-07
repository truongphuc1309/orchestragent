package com.orchestragent.daemon.provider;

import java.util.Optional;

/**
 * An AI tool the daemon can run, such as Claude Code. Implementations are found with {@link java.util.ServiceLoader}:
 * a provider module lists its class in {@code META-INF/services/com.orchestragent.daemon.provider.AgentProvider},
 * so adding a tool means adding a module, without changing the daemon. A provider needs a public no-argument
 * constructor.
 *
 * <p>One provider object runs several tasks at the same time, each on its own thread, so {@link #run} must not keep
 * per-run state in fields.
 */
public interface AgentProvider {

    /**
     * The short lower-case code of the tool, such as {@code claude} or {@code mock}. The server stores it on the
     * runtime, and it must match {@code [a-z0-9][a-z0-9._-]{0,49}}.
     */
    String id();

    /**
     * Receives the provider's settings from the daemon's {@code config.json}, once, before {@link #detect()} and
     * {@link #run}. The default ignores them.
     *
     * @throws IllegalArgumentException when a setting is wrong; the message is shown to the user
     */
    default void configure(ProviderSettings settings) {
    }

    /** Looks for the tool on this computer. Empty when it is not installed or does not answer. Must not throw. */
    Optional<ProviderInfo> detect();

    /**
     * Whether {@code start} uses this provider when the user does not list providers. A pretend tool (the mock)
     * says no, so it only runs when asked for by name.
     */
    default boolean enabledByDefault() {
        return true;
    }

    /**
     * Runs the tool once and waits until it finishes, fails, times out ({@link RunRequest#timeout()}) or is
     * cancelled. Progress goes to {@code listener} as it happens.
     *
     * <p>A failure of the tool is a {@link RunResult#failed failed result}, not an exception. When {@code cancel}
     * fires, the provider stops the tool as soon as it can and returns; what it returns then is not reported.
     */
    RunResult run(RunRequest request, RunListener listener, CancellationToken cancel);
}
