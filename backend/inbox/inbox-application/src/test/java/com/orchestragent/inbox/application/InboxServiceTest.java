package com.orchestragent.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.inbox.application.InboxFakes.FakeAccess;
import com.orchestragent.inbox.application.InboxFakes.InMemoryNotifications;
import com.orchestragent.inbox.application.InboxFakes.MutableClock;
import com.orchestragent.inbox.application.InboxFakes.Published;
import com.orchestragent.inbox.application.InboxService.Inbox;
import com.orchestragent.inbox.application.InboxService.NotificationView;
import com.orchestragent.inbox.domain.Notification;
import com.orchestragent.inbox.domain.NotificationId;
import com.orchestragent.inbox.domain.NotificationType;
import com.orchestragent.shared.error.NotFoundException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxServiceTest {

    private final UUID workspace = UUID.randomUUID();
    private final UUID mia = UUID.randomUUID();
    private final UUID max = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();
    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final FakeAccess access = new FakeAccess();
    private final Published published = new Published();
    private final InboxService inbox = new InboxService(notifications, access, new InboxEvents(published, clock), clock);

    InboxServiceTest() {
        access.join(workspace, mia);
        access.join(workspace, max);
    }

    private Notification give(UUID workspaceId, UUID recipient, int number) {
        Notification n = Notification.create(new NotificationId(UUID.randomUUID()), workspaceId, recipient,
                NotificationType.ISSUE_ASSIGNED, UUID.randomUUID(), number, "Issue " + number, "Olivia", null,
                UUID.randomUUID(), clock.instant());
        notifications.addOnce(n);
        clock.advance(Duration.ofMinutes(1));
        return n;
    }

    @Test
    void showsOnlyMyNotificationsOfThisWorkspaceNewestFirst() {
        give(workspace, mia, 1);
        give(workspace, max, 2);
        give(UUID.randomUUID(), mia, 3);
        give(workspace, mia, 4);

        Inbox mine = inbox.inbox(mia, workspace);

        assertThat(mine.notifications()).extracting(NotificationView::issueNumber).containsExactly(4, 1);
        assertThat(mine.unreadCount()).isEqualTo(2);
    }

    @Test
    void markingReadCountsDownAndAnnouncesOnce() {
        Notification first = give(workspace, mia, 1);
        give(workspace, mia, 2);

        NotificationView read = inbox.markRead(mia, workspace, first.id().value());
        inbox.markRead(mia, workspace, first.id().value());

        assertThat(read.readAt()).isEqualTo(clock.instant());
        assertThat(inbox.unreadCount(mia, workspace)).isEqualTo(1);
        assertThat(published.recipients()).containsExactly(mia);
    }

    @Test
    void someoneElsesNotificationIsNotFound() {
        Notification maxs = give(workspace, max, 1);

        assertThatThrownBy(() -> inbox.markRead(mia, workspace, maxs.id().value())).isInstanceOf(NotFoundException.class);
        assertThat(maxs.isRead()).isFalse();
    }

    @Test
    void markAllReadTouchesOnlyMine() {
        give(workspace, mia, 1);
        give(workspace, mia, 2);
        give(workspace, max, 3);

        inbox.markAllRead(mia, workspace);
        inbox.markAllRead(mia, workspace);

        assertThat(inbox.unreadCount(mia, workspace)).isZero();
        assertThat(inbox.unreadCount(max, workspace)).isEqualTo(1);
        assertThat(published.recipients()).containsExactly(mia);
    }

    @Test
    void outsidersHaveNoInbox() {
        UUID outsider = UUID.randomUUID();

        assertThatThrownBy(() -> inbox.inbox(outsider, workspace)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inbox.unreadCount(outsider, workspace)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inbox.markAllRead(outsider, workspace)).isInstanceOf(NotFoundException.class);
    }
}
