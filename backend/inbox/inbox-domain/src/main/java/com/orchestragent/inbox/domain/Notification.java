package com.orchestragent.inbox.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Something one person should know about an issue. It keeps a copy of what it says (issue number and title, who
 * did it, a detail such as the failure reason), so it still reads right after the issue or the agent changed.
 * {@code sourceEventId} is the event that caused it: one event makes at most one notification per person, even
 * when it is delivered twice.
 */
public final class Notification {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DETAIL_LENGTH = 500;

    private final NotificationId id;
    private final UUID workspaceId;
    private final UUID recipientId;
    private final NotificationType type;
    private final UUID issueId;
    private final String issueKey;
    private final String issueTitle;
    private final String actorName;
    private final String detail;
    private final UUID sourceEventId;
    private final Instant createdAt;
    private Instant readAt;

    private Notification(
            NotificationId id,
            UUID workspaceId,
            UUID recipientId,
            NotificationType type,
            UUID issueId,
            String issueKey,
            String issueTitle,
            String actorName,
            String detail,
            UUID sourceEventId,
            Instant createdAt,
            Instant readAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.type = Objects.requireNonNull(type, "type");
        this.issueId = Objects.requireNonNull(issueId, "issueId");
        this.issueKey = Objects.requireNonNull(issueKey, "issueKey");
        this.issueTitle = cut(Objects.requireNonNull(issueTitle, "issueTitle"), MAX_TITLE_LENGTH);
        this.actorName = cut(Objects.requireNonNull(actorName, "actorName"), MAX_NAME_LENGTH);
        this.detail = detail == null || detail.isBlank() ? null : cut(detail.strip(), MAX_DETAIL_LENGTH);
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.readAt = readAt;
    }

    /** A new, unread notification. */
    public static Notification create(
            NotificationId id,
            UUID workspaceId,
            UUID recipientId,
            NotificationType type,
            UUID issueId,
            String issueKey,
            String issueTitle,
            String actorName,
            String detail,
            UUID sourceEventId,
            Instant now) {
        return new Notification(id, workspaceId, recipientId, type, issueId, issueKey, issueTitle, actorName, detail,
                sourceEventId, now, null);
    }

    /** A stored notification, as the repository reads it back. */
    public static Notification restore(
            NotificationId id,
            UUID workspaceId,
            UUID recipientId,
            NotificationType type,
            UUID issueId,
            String issueKey,
            String issueTitle,
            String actorName,
            String detail,
            UUID sourceEventId,
            Instant createdAt,
            Instant readAt) {
        return new Notification(id, workspaceId, recipientId, type, issueId, issueKey, issueTitle, actorName, detail,
                sourceEventId, createdAt, readAt);
    }

    /**
     * Marks it read.
     *
     * @return false when it was read already (nothing changes)
     */
    public boolean markRead(Instant now) {
        if (readAt != null) {
            return false;
        }
        readAt = Objects.requireNonNull(now, "now");
        return true;
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String cut(String text, int max) {
        String value = text.strip();
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public NotificationId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID recipientId() {
        return recipientId;
    }

    public NotificationType type() {
        return type;
    }

    public UUID issueId() {
        return issueId;
    }

    public String issueKey() {
        return issueKey;
    }

    public String issueTitle() {
        return issueTitle;
    }

    public String actorName() {
        return actorName;
    }

    public String detail() {
        return detail;
    }

    public UUID sourceEventId() {
        return sourceEventId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant readAt() {
        return readAt;
    }
}
