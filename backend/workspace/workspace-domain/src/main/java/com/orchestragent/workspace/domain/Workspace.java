package com.orchestragent.workspace.domain;

import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Workspace {

    public static final int MAX_NAME_LENGTH = 60;

    private final WorkspaceId id;
    private final String name;
    private final String slug;
    private final UUID createdBy;
    private final Instant createdAt;

    public Workspace(WorkspaceId id, String name, String slug, UUID createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Workspace create(String rawName, String slug, UUID createdBy, Instant now) {
        return new Workspace(WorkspaceId.generate(), validName(rawName), slug, createdBy, now);
    }

    public static String validName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "workspace.invalid_name", "Workspace name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        // The name ends up in email subjects, where a line break would allow header injection.
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("workspace.invalid_name", "Workspace name must not contain control characters");
        }
        return name;
    }

    public WorkspaceId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
