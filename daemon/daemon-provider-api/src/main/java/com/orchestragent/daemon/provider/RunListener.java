package com.orchestragent.daemon.provider;

/**
 * Receives the progress of a run, line by line, in order. The daemon numbers the lines and sends them to the server
 * in batches. Calls may come from any thread of the provider, but not at the same time for one run unless the
 * implementation says it is thread-safe (the daemon's is).
 */
@FunctionalInterface
public interface RunListener {

    void onMessage(MessageType type, String content);
}
