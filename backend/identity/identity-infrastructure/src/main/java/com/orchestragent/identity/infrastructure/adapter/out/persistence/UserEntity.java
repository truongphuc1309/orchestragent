package com.orchestragent.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Read model of a user row. Rows are inserted and changed with SQL in {@link UserRepositoryAdapter}. */
@Entity
@Table(schema = "identity", name = "users")
class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String bio;

    @Column(name = "avatar_id")
    private UUID avatarId;

    @Column(name = "avatar_type")
    private String avatarType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getName() {
        return name;
    }

    String getBio() {
        return bio;
    }

    UUID getAvatarId() {
        return avatarId;
    }

    String getAvatarType() {
        return avatarType;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
