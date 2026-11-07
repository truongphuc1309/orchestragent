package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "api_tokens")
class ApiTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "token_prefix", nullable = false)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApiTokenEntity() {
    }

    ApiTokenEntity(
            UUID id,
            UUID userId,
            String name,
            String tokenPrefix,
            String tokenHash,
            Instant lastUsedAt,
            Instant revokedAt,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
        this.createdAt = createdAt;
    }

    void updateFrom(Instant lastUsedAt, Instant revokedAt) {
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getName() {
        return name;
    }

    String getTokenPrefix() {
        return tokenPrefix;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
