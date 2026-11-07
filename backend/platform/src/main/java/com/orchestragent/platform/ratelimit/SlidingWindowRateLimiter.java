package com.orchestragent.platform.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Allows at most {@code maxRequests} per key (for example a client address) in any window of the given
 * length. Kept in memory, so it protects one server instance; with several instances each one counts on its
 * own, which only makes the limit looser, never stricter.
 *
 * <p>Memory is bounded: keys whose requests have all left the window are dropped, and when {@link
 * #MAX_KEYS} keys are tracked, idle ones are forgotten first.
 */
public final class SlidingWindowRateLimiter {

    /** Most distinct keys tracked at once. A flood of addresses cannot grow the map without limit. */
    static final int MAX_KEYS = 50_000;

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new HashMap<>();

    public SlidingWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be at least 1");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Counts one request for the key.
     *
     * @return empty when the request is allowed, otherwise the number of seconds (at least 1) until the oldest
     *         counted request leaves the window and the key may try again
     */
    public synchronized OptionalLong tryAcquire(String key) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        if (requests.size() >= MAX_KEYS && !requests.containsKey(key)) {
            dropIdleKeys(cutoff);
        }
        Deque<Instant> times = requests.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }

        if (times.size() < maxRequests) {
            times.addLast(now);
            return OptionalLong.empty();
        }
        // Rejected requests are not counted: the wait ends when the oldest counted request leaves the window.
        long wait = Duration.between(now, times.peekFirst().plus(window)).toSeconds() + 1;
        return OptionalLong.of(Math.max(1, wait));
    }

    private void dropIdleKeys(Instant cutoff) {
        requests.values().removeIf(times -> times.isEmpty() || !times.peekLast().isAfter(cutoff));
        if (requests.size() >= MAX_KEYS) {
            // Everything is busy: make room by forgetting arbitrary keys rather than refusing new clients.
            var iterator = requests.keySet().iterator();
            for (int i = 0; i < MAX_KEYS / 10 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }
    }
}
