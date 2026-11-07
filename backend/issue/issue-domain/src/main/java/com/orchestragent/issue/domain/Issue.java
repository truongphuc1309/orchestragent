package com.orchestragent.issue.domain;

import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A unit of work. The mutating methods return whether anything changed, so callers only publish events and
 * touch {@code updatedAt} for real changes.
 */
public final class Issue {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 20_000;

    private final IssueId id;
    private final UUID workspaceId;
    private final int number;
    private final Actor creator;
    private final Instant createdAt;
    private String title;
    private String description;
    private Status status;
    private Priority priority;
    private Actor assignee;
    private double position;
    private Instant updatedAt;

    public Issue(
            IssueId id,
            UUID workspaceId,
            int number,
            String title,
            String description,
            Status status,
            Priority priority,
            Actor assignee,
            Actor creator,
            double position,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.number = number;
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.status = Objects.requireNonNull(status, "status");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.assignee = assignee;
        this.creator = Objects.requireNonNull(creator, "creator");
        this.position = position;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Issue create(
            UUID workspaceId,
            int number,
            String title,
            String description,
            Status status,
            Priority priority,
            Actor assignee,
            Actor creator,
            double position,
            Instant now) {
        return new Issue(
                IssueId.generate(),
                workspaceId,
                number,
                validTitle(title),
                validDescription(description),
                status,
                priority,
                assignee,
                creator,
                position,
                now,
                now);
    }

    /** Trims the title and checks it is a single line of 1 to {@value #MAX_TITLE_LENGTH} characters. */
    public static String validTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(
                    "issue.invalid_title", "Title must be 1 to " + MAX_TITLE_LENGTH + " characters");
        }
        if (title.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("issue.invalid_title", "Title must be a single line of text");
        }
        return title;
    }

    /** Descriptions may be empty and may span lines; they are only length-limited. */
    public static String validDescription(String rawDescription) {
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException(
                    "issue.invalid_description",
                    "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return description;
    }

    public boolean rename(String newTitle, Instant now) {
        String valid = validTitle(newTitle);
        if (valid.equals(title)) {
            return false;
        }
        title = valid;
        updatedAt = now;
        return true;
    }

    public boolean describe(String newDescription, Instant now) {
        String valid = validDescription(newDescription);
        if (valid.equals(description)) {
            return false;
        }
        description = valid;
        updatedAt = now;
        return true;
    }

    public boolean changePriority(Priority newPriority, Instant now) {
        Objects.requireNonNull(newPriority, "newPriority");
        if (newPriority == priority) {
            return false;
        }
        priority = newPriority;
        updatedAt = now;
        return true;
    }

    /** Sets the assignee; null unassigns. */
    public boolean assign(Actor newAssignee, Instant now) {
        if (Objects.equals(newAssignee, assignee)) {
            return false;
        }
        assignee = newAssignee;
        updatedAt = now;
        return true;
    }

    /**
     * Moves the issue to a column and a place in it. Any status may follow any other: people reopen, park
     * and cancel work freely, and the board is how they do it.
     */
    public boolean moveTo(Status newStatus, double newPosition, Instant now) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (newStatus == status && Double.compare(newPosition, position) == 0) {
            return false;
        }
        status = newStatus;
        position = newPosition;
        updatedAt = now;
        return true;
    }

    /**
     * Gives the issue a new place in its column without counting as an edit (used when a column is
     * renumbered, which nobody asked for).
     */
    public void renumber(double newPosition) {
        position = newPosition;
    }

    public IssueId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public int number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Status status() {
        return status;
    }

    public Priority priority() {
        return priority;
    }

    /** The assignee, or null when the issue is unassigned. */
    public Actor assignee() {
        return assignee;
    }

    public Actor creator() {
        return creator;
    }

    public double position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
