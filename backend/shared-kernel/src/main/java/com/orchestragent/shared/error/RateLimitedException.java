package com.orchestragent.shared.error;

/** The caller did the same thing too often. Maps to 429. */
public class RateLimitedException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        this("rate_limited", message, retryAfterSeconds);
    }

    public RateLimitedException(String code, String message, long retryAfterSeconds) {
        super(code, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
