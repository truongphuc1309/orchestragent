package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A group of issues inside a workspace. Every issue belongs to one project, and its key ({@code WEB-12}) is the
 * project key and a number counted per project. The key is fixed; the name and description may change.
 *
 * <p>Projects are archived, never deleted, so their issues stay readable and their key is never reused. An
 * archived project takes no new issues and cannot be edited until it is restored.
 */
public final class Project {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final ProjectId id;
    private final UUID workspaceId;
    private final ProjectKey key;
    private final UUID createdBy;
    private final Instant createdAt;
    private String name;
    private String description;
    private Instant archivedAt;
    private Instant updatedAt;

    public Project(
            ProjectId id,
            UUID workspaceId,
            ProjectKey key,
            String name,
            String description,
            UUID createdBy,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.archivedAt = archivedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Project create(
            UUID workspaceId, ProjectKey key, String name, String description, UUID createdBy, Instant now) {
        return new Project(
                ProjectId.generate(),
                workspaceId,
                key,
                validName(name),
                validDescription(description),
                createdBy,
                null,
                now,
                now);
    }

    /** Trims the name and checks it is a single line of 1 to {@value #MAX_NAME_LENGTH} characters. */
    public static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "project.invalid_name", "Name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("project.invalid_name", "Name must be a single line of text");
        }
        return name;
    }

    /** Descriptions may be empty and may span lines; they are only length-limited. */
    public static String validDescription(String raw) {
        String description = raw == null ? "" : raw.strip();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException(
                    "project.invalid_description",
                    "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return description;
    }

    public boolean rename(String newName, Instant now) {
        requireActive();
        String valid = validName(newName);
        if (valid.equals(name)) {
            return false;
        }
        name = valid;
        updatedAt = now;
        return true;
    }

    public boolean describe(String newDescription, Instant now) {
        requireActive();
        String valid = validDescription(newDescription);
        if (valid.equals(description)) {
            return false;
        }
        description = valid;
        updatedAt = now;
        return true;
    }

    /** Returns whether the project was active before. */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = now;
        updatedAt = now;
        return true;
    }

    /** Returns whether the project was archived before. */
    public boolean restore(Instant now) {
        if (archivedAt == null) {
            return false;
        }
        archivedAt = null;
        updatedAt = now;
        return true;
    }

    /** @throws ConflictException when the project is archived */
    public void requireActive() {
        if (archivedAt != null) {
            throw new ConflictException(
                    "project.archived", "This project is archived. Restore it before changing it or adding issues.");
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public ProjectId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public ProjectKey key() {
        return key;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public UUID createdBy() {
        return createdBy;
    }

    /** When the project was archived, or null while it is active. */
    public Instant archivedAt() {
        return archivedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
