package com.orchestragent.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable. Changes go through {@link UserRepository} (one column group at a time, so they never overwrite each other). */
public final class User {

    private final UserId id;
    private final Email email;
    private final String name;
    private final String bio;
    private final Avatar avatar;
    private final Instant createdAt;

    /** @param avatar null when the user has no picture */
    public User(UserId id, Email email, String name, String bio, Avatar avatar, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.name = Objects.requireNonNull(name, "name");
        this.bio = Objects.requireNonNull(bio, "bio");
        this.avatar = avatar;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A user created the first time an email address completes the login flow. */
    public static User register(Email email, Instant now) {
        return new User(UserId.generate(), email, email.localPart(), "", null, now);
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

    /** Free text about the user ("About you"); empty when not set. */
    public String bio() {
        return bio;
    }

    public Optional<Avatar> avatar() {
        return Optional.ofNullable(avatar);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
