package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "login_codes")
class LoginCodeEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginCodeEntity() {
    }

    LoginCodeEntity(
            UUID id, String email, String codeHash, Instant expiresAt, int attempts, Instant usedAt, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attempts = attempts;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    /** Copies the mutable state of the domain object onto this managed entity. */
    void updateFrom(int attempts, Instant usedAt) {
        this.attempts = attempts;
        this.usedAt = usedAt;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getCodeHash() {
        return codeHash;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getUsedAt() {
        return usedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
