package com.orchestragent.inbox.application;

import com.orchestragent.inbox.domain.Notification;
import com.orchestragent.inbox.domain.NotificationId;
import com.orchestragent.inbox.domain.NotificationRepository;
import com.orchestragent.inbox.domain.NotificationType;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.workspace.contract.WorkspaceAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's inbox in a workspace: the newest notifications, how many are unread, and marking them read. Only
 * members have one, and nobody sees or touches another person's notifications ("not found").
 */
@Service
public class InboxService {

    /** The inbox shows this many of the newest notifications. */
    public static final int PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final WorkspaceAccess access;
    private final InboxEvents events;
    private final Clock clock;

    InboxService(NotificationRepository notifications, WorkspaceAccess access, InboxEvents events, Clock clock) {
        this.notifications = notifications;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    public record NotificationView(
            UUID id,
            NotificationType type,
            UUID issueId,
            String issueKey,
            String issueTitle,
            String actorName,
            String detail,
            Instant createdAt,
            Instant readAt) {

        static NotificationView from(Notification n) {
            return new NotificationView(n.id().value(), n.type(), n.issueId(), n.issueKey(), n.issueTitle(),
                    n.actorName(), n.detail(), n.createdAt(), n.readAt());
        }
    }

    public record Inbox(List<NotificationView> notifications, int unreadCount) {
    }

    @Transactional(readOnly = true)
    public Inbox inbox(UUID userId, UUID workspaceId) {
        access.requireMember(workspaceId, userId);
        return new Inbox(
                notifications.findLatest(workspaceId, userId, PAGE_SIZE).stream().map(NotificationView::from).toList(),
                notifications.countUnread(workspaceId, userId));
    }

    @Transactional(readOnly = true)
    public int unreadCount(UUID userId, UUID workspaceId) {
        access.requireMember(workspaceId, userId);
        return notifications.countUnread(workspaceId, userId);
    }

    @Transactional
    public NotificationView markRead(UUID userId, UUID workspaceId, UUID notificationId) {
        access.requireMember(workspaceId, userId);
        Notification notification = notifications.find(workspaceId, userId, new NotificationId(notificationId))
                .orElseThrow(() -> new NotFoundException("notification.not_found", "Notification not found"));
        if (notification.markRead(clock.instant())) {
            notifications.save(notification);
            events.changed(workspaceId, userId);
        }
        return NotificationView.from(notification);
    }

    @Transactional
    public void markAllRead(UUID userId, UUID workspaceId) {
        access.requireMember(workspaceId, userId);
        if (notifications.markAllRead(workspaceId, userId, clock.instant()) > 0) {
            events.changed(workspaceId, userId);
        }
    }
}
