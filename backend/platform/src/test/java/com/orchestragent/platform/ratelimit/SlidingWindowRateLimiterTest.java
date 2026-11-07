package com.orchestragent.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, WINDOW, clock);

    @Test
    void allowsUpToTheLimitAndRejectsTheNextRequest() {
        assertThat(limiter.tryAcquire("1.2.3.4")).isEmpty();
        assertThat(limiter.tryAcquire("1.2.3.4")).isEmpty();
        assertThat(limiter.tryAcquire("1.2.3.4")).isEmpty();

        assertThat(limiter.tryAcquire("1.2.3.4")).isPresent();
    }

    @Test
    void countsEveryKeyOnItsOwn() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.2.3.4");
        }

        assertThat(limiter.tryAcquire("1.2.3.4")).isPresent();
        assertThat(limiter.tryAcquire("5.6.7.8")).isEmpty();
    }

    @Test
    void allowsAgainWhenTheOldestRequestLeavesTheWindow() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(4));
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertThat(limiter.tryAcquire("a")).isPresent();

        clock.advance(Duration.ofMinutes(6).plusSeconds(1));

        assertThat(limiter.tryAcquire("a")).isEmpty();
        assertThat(limiter.tryAcquire("a")).as("only one slot was free").isPresent();
    }

    @Test
    void tellsHowLongToWaitForTheOldestRequestToExpire() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(4));
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        OptionalLong wait = limiter.tryAcquire("a");

        // The first request leaves the window 6 minutes from now.
        assertThat(wait).isPresent();
        assertThat(wait.getAsLong()).isBetween(360L, 362L);
    }

    @Test
    void rejectedRequestsDoNotExtendTheBlock() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire("a")).isPresent();
        }

        clock.advance(WINDOW.plusSeconds(1));

        assertThat(limiter.tryAcquire("a")).isEmpty();
    }

    @Test
    void waitIsAtLeastOneSecond() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        clock.advance(WINDOW.minusMillis(10));

        assertThat(limiter.tryAcquire("a").getAsLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void trackedKeysStayBounded() {
        SlidingWindowRateLimiter many = new SlidingWindowRateLimiter(1, WINDOW, clock);
        for (int i = 0; i < SlidingWindowRateLimiter.MAX_KEYS + 100; i++) {
            assertThat(many.tryAcquire("ip-" + i)).isEmpty();
        }

        // Still working after the cap was hit: a new client is served, not refused.
        assertThat(many.tryAcquire("newcomer")).isEmpty();
    }

    @Test
    void rejectsSettingsThatCannotWork() {
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(0, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
