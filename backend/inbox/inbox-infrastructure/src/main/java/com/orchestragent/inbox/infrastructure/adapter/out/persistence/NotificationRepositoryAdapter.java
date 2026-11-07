package com.orchestragent.inbox.infrastructure.adapter.out.persistence;

import com.orchestragent.inbox.domain.Notification;
import com.orchestragent.inbox.domain.NotificationId;
import com.orchestragent.inbox.domain.NotificationRepository;
import com.orchestragent.inbox.domain.NotificationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Notifications in {@code inbox.notifications}, with plain SQL. */
@Repository
class NotificationRepositoryAdapter implements NotificationRepository {

    /** A second delivery of the same event for the same person is skipped by the database itself, even in a race. */
    private static final String INSERT = """
            INSERT INTO inbox.notifications (id, workspace_id, recipient_user_id, type, issue_id, issue_number,
                issue_title, actor_name, detail, source_event_id, read_at, created_at)
            VALUES (:id, :workspaceId, :recipientId, :type, :issueId, :issueNumber, :issueTitle, :actorName, :detail,
                :sourceEventId, :readAt, :createdAt)
            ON CONFLICT (source_event_id, recipient_user_id) DO NOTHING
            """;

    private static final String COLUMNS = """
            id, workspace_id, recipient_user_id, type, issue_id, issue_number, issue_title, actor_name, detail,
            source_event_id, read_at, created_at
            """;

    private final JdbcClient jdbc;

    NotificationRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean addOnce(Notification n) {
        return jdbc.sql(INSERT)
                .param("id", n.id().value())
                .param("workspaceId", n.workspaceId())
                .param("recipientId", n.recipientId())
                .param("type", n.type().code())
                .param("issueId", n.issueId())
                .param("issueNumber", n.issueNumber())
                .param("issueTitle", n.issueTitle())
                .param("actorName", n.actorName())
                .param("detail", n.detail())
                .param("sourceEventId", n.sourceEventId())
                .param("readAt", utc(n.readAt()))
                .param("createdAt", utc(n.createdAt()))
                .update() == 1;
    }

    @Override
    public Optional<Notification> find(UUID workspaceId, UUID recipientId, NotificationId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM inbox.notifications"
                        + " WHERE id = :id AND workspace_id = :workspaceId AND recipient_user_id = :recipientId")
                .param("id", id.value())
                .param("workspaceId", workspaceId)
                .param("recipientId", recipientId)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    public List<Notification> findLatest(UUID workspaceId, UUID recipientId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM inbox.notifications"
                        + " WHERE workspace_id = :workspaceId AND recipient_user_id = :recipientId"
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("workspaceId", workspaceId)
                .param("recipientId", recipientId)
                .param("limit", limit)
                .query((rs, row) -> map(rs))
                .list();
    }

    @Override
    public int countUnread(UUID workspaceId, UUID recipientId) {
        return jdbc.sql("SELECT count(*) FROM inbox.notifications"
                        + " WHERE workspace_id = :workspaceId AND recipient_user_id = :recipientId AND read_at IS NULL")
                .param("workspaceId", workspaceId)
                .param("recipientId", recipientId)
                .query(Integer.class)
                .single();
    }

    @Override
    public void save(Notification n) {
        jdbc.sql("UPDATE inbox.notifications SET read_at = :readAt WHERE id = :id")
                .param("id", n.id().value())
                .param("readAt", utc(n.readAt()))
                .update();
    }

    @Override
    public int markAllRead(UUID workspaceId, UUID recipientId, Instant now) {
        return jdbc.sql("UPDATE inbox.notifications SET read_at = :now"
                        + " WHERE workspace_id = :workspaceId AND recipient_user_id = :recipientId AND read_at IS NULL")
                .param("workspaceId", workspaceId)
                .param("recipientId", recipientId)
                .param("now", utc(now))
                .update();
    }

    @Override
    public List<UUID> deleteByIssue(UUID workspaceId, UUID issueId) {
        return jdbc.sql("DELETE FROM inbox.notifications WHERE workspace_id = :workspaceId AND issue_id = :issueId"
                        + " RETURNING recipient_user_id")
                .param("workspaceId", workspaceId)
                .param("issueId", issueId)
                .query(UUID.class)
                .list()
                .stream()
                .distinct()
                .toList();
    }

    private static Notification map(ResultSet rs) throws SQLException {
        OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
        return Notification.restore(
                new NotificationId(rs.getObject("id", UUID.class)),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("recipient_user_id", UUID.class),
                NotificationType.fromCode(rs.getString("type")),
                rs.getObject("issue_id", UUID.class),
                rs.getInt("issue_number"),
                rs.getString("issue_title"),
                rs.getString("actor_name"),
                rs.getString("detail"),
                rs.getObject("source_event_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                readAt == null ? null : readAt.toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
