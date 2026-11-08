package com.orchestragent.inbox.infrastructure.adapter.in.web;

import com.orchestragent.inbox.application.InboxService;
import com.orchestragent.inbox.application.InboxService.Inbox;
import com.orchestragent.inbox.application.InboxService.NotificationView;
import com.orchestragent.platform.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's inbox in a workspace. */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/inbox")
class InboxController {

    private final InboxService inbox;

    InboxController(InboxService inbox) {
        this.inbox = inbox;
    }

    /** {@code type} is issue_assigned, agent_completed or agent_failed; {@code detail} is the failure reason. */
    record NotificationResponse(
            UUID id,
            String type,
            UUID issueId,
            String issueKey,
            String issueTitle,
            String actorName,
            String detail,
            Instant createdAt,
            Instant readAt) {

        static NotificationResponse from(NotificationView view) {
            return new NotificationResponse(view.id(), view.type().code(), view.issueId(), view.issueKey(),
                    view.issueTitle(), view.actorName(), view.detail(), view.createdAt(), view.readAt());
        }
    }

    record InboxResponse(List<NotificationResponse> notifications, int unreadCount) {

        static InboxResponse from(Inbox inbox) {
            return new InboxResponse(inbox.notifications().stream().map(NotificationResponse::from).toList(), inbox.unreadCount());
        }
    }

    record UnreadCountResponse(int unreadCount) {
    }

    /** The newest notifications (at most 100) and how many are unread. */
    @GetMapping
    InboxResponse list(CurrentUser user, @PathVariable UUID workspaceId) {
        return InboxResponse.from(inbox.inbox(user.id(), workspaceId));
    }

    /** Only the count, for the badge in the navigation. */
    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(CurrentUser user, @PathVariable UUID workspaceId) {
        return new UnreadCountResponse(inbox.unreadCount(user.id(), workspaceId));
    }

    @PostMapping("/{notificationId}/read")
    NotificationResponse markRead(CurrentUser user, @PathVariable UUID workspaceId, @PathVariable UUID notificationId) {
        return NotificationResponse.from(inbox.markRead(user.id(), workspaceId, notificationId));
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> markAllRead(CurrentUser user, @PathVariable UUID workspaceId) {
        inbox.markAllRead(user.id(), workspaceId);
        return ResponseEntity.noContent().build();
    }
}
