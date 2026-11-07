package com.orchestragent.identity.domain;

import java.time.Instant;
import java.util.Objects;

public final class User {

    private final UserId id;
    private final Email email;
    private final String name;
    private final Instant createdAt;

    public User(UserId id, Email email, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.name = Objects.requireNonNull(name, "name");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A user created the first time an email address completes the login flow. */
    public static User register(Email email, Instant now) {
        return new User(UserId.generate(), email, email.localPart(), now);
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
