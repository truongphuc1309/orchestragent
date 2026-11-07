package com.orchestragent.inbox.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores notifications. Every query is for one person in one workspace. */
public interface NotificationRepository {

    /**
     * Stores a new notification unless the same event already made one for the same person.
     *
     * @return true when it was stored
     */
    boolean addOnce(Notification notification);

    Optional<Notification> find(UUID workspaceId, UUID recipientId, NotificationId id);

    /** The newest first, at most {@code limit}. */
    List<Notification> findLatest(UUID workspaceId, UUID recipientId, int limit);

    int countUnread(UUID workspaceId, UUID recipientId);

    /** Saves a read mark. */
    void save(Notification notification);

    /** @return how many were unread */
    int markAllRead(UUID workspaceId, UUID recipientId, Instant now);

    /** Deletes the notifications about an issue; returns the people who had some. */
    List<UUID> deleteByIssue(UUID workspaceId, UUID issueId);
}
