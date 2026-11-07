package com.orchestragent.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A one-time login code sent by email. Only a hash of the code is stored. A code is usable once, for a
 * limited time, and after a limited number of wrong guesses it is locked.
 */
public final class LoginCode {

    public static final Duration TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;

    private final LoginCodeId id;
    private final Email email;
    private final String codeHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private int attempts;
    private Instant usedAt;

    public LoginCode(
            LoginCodeId id,
            Email email,
            String codeHash,
            Instant expiresAt,
            int attempts,
            Instant usedAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.attempts = attempts;
        this.usedAt = usedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static LoginCode issue(Email email, String codeHash, Instant now) {
        return new LoginCode(LoginCodeId.generate(), email, codeHash, now.plus(TTL), 0, null, now);
    }

    /** True while the code has not been used, has not expired and still has guesses left. */
    public boolean canBeTried(Instant now) {
        return usedAt == null && now.isBefore(expiresAt) && attempts < MAX_ATTEMPTS;
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public LoginCodeId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public int attempts() {
        return attempts;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
