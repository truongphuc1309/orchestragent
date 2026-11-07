package com.orchestragent.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A long-lived credential for the daemon. The raw token is shown once at creation; only its SHA-256 hash and
 * a short display prefix are stored.
 */
public final class ApiToken {

    /** Updating {@code last_used_at} on every request would turn each daemon call into a write. */
    private static final Duration LAST_USED_GRANULARITY = Duration.ofMinutes(1);

    private final ApiTokenId id;
    private final UserId userId;
    private final String name;
    private final String tokenPrefix;
    private final String tokenHash;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    public ApiToken(
            ApiTokenId id,
            UserId userId,
            String name,
            String tokenPrefix,
            String tokenHash,
            Instant lastUsedAt,
            Instant revokedAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.name = Objects.requireNonNull(name, "name");
        this.tokenPrefix = Objects.requireNonNull(tokenPrefix, "tokenPrefix");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ApiToken create(UserId userId, String name, String tokenPrefix, String tokenHash, Instant now) {
        return new ApiToken(ApiTokenId.generate(), userId, name, tokenPrefix, tokenHash, null, null, now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isOwnedBy(UserId user) {
        return userId.equals(user);
    }

    /** Revoking twice keeps the first revocation time. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** @return true when the stored time changed and the token needs to be saved */
    public boolean recordUse(Instant now) {
        if (lastUsedAt == null || lastUsedAt.plus(LAST_USED_GRANULARITY).isBefore(now)) {
            lastUsedAt = now;
            return true;
        }
        return false;
    }

    public ApiTokenId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public String tokenPrefix() {
        return tokenPrefix;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
