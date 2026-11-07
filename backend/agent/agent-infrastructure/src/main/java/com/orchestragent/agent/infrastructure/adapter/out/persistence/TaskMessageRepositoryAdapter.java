package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import com.orchestragent.agent.domain.MessageType;
import com.orchestragent.agent.domain.TaskId;
import com.orchestragent.agent.domain.TaskMessage;
import com.orchestragent.agent.domain.TaskMessageRepository;
import com.orchestragent.shared.id.UuidV7;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TaskMessageRepositoryAdapter implements TaskMessageRepository {

    /** A number that is already stored is skipped by the database itself, so resending a batch is safe even when two requests race. */
    private static final String INSERT = """
            INSERT INTO agent.task_messages (id, task_id, seq, type, content, created_at)
            VALUES (:id, :taskId, :seq, :type, :content, :createdAt)
            ON CONFLICT (task_id, seq) DO NOTHING
            """;

    private static final String FIND_AFTER = """
            SELECT seq, type, content, created_at
            FROM agent.task_messages
            WHERE task_id = :taskId AND seq > :after
            ORDER BY seq
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    TaskMessageRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int append(TaskId taskId, List<TaskMessage> messages) {
        int added = 0;
        for (TaskMessage message : messages) {
            added += jdbc.sql(INSERT)
                    .param("id", UuidV7.next())
                    .param("taskId", taskId.value())
                    .param("seq", message.seq())
                    .param("type", message.type().code())
                    .param("content", message.content())
                    .param("createdAt", OffsetDateTime.ofInstant(message.createdAt(), ZoneOffset.UTC))
                    .update();
        }
        return added;
    }

    @Override
    public List<TaskMessage> findAfter(TaskId taskId, long afterSeq, int limit) {
        return jdbc.sql(FIND_AFTER)
                .param("taskId", taskId.value())
                .param("after", afterSeq)
                .param("limit", limit)
                .query((rs, row) -> new TaskMessage(
                        rs.getLong("seq"),
                        MessageType.valueOf(rs.getString("type").toUpperCase(java.util.Locale.ROOT)),
                        rs.getString("content"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    public OptionalLong lastSeq(TaskId taskId) {
        // MAX over no rows is one row holding NULL, which optional() reports as empty.
        return jdbc.sql("SELECT MAX(seq) FROM agent.task_messages WHERE task_id = :taskId")
                .param("taskId", taskId.value())
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }
}
