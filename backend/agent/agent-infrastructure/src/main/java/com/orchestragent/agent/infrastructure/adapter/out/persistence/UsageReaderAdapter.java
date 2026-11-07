package com.orchestragent.agent.infrastructure.adapter.out.persistence;

import com.orchestragent.agent.application.UsageReader;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The usage sums, computed by Postgres over {@code agent.agent_tasks}. */
@Repository
class UsageReaderAdapter implements UsageReader {

    /** The columns every query returns, over the runs that ended in the period. */
    private static final String SUMS = """
            count(*) AS runs,
            count(*) FILTER (WHERE t.status = 'completed') AS completed,
            count(*) FILTER (WHERE t.status = 'failed') AS failed,
            COALESCE(sum(t.input_tokens), 0) AS input_tokens,
            COALESCE(sum(t.output_tokens), 0) AS output_tokens,
            COALESCE(sum(t.cost_usd), 0) AS cost_usd
            """;

    private static final String ENDED = """
            t.workspace_id = :workspaceId
            AND t.finished_at >= :from
            AND t.status IN ('completed', 'failed', 'cancelled')
            """;

    private static final String TOTALS = "SELECT " + SUMS + " FROM agent.agent_tasks t WHERE " + ENDED;

    private static final String BY_AGENT = "SELECT t.agent_id, a.name, a.archived_at IS NOT NULL AS archived, " + SUMS
            + " FROM agent.agent_tasks t JOIN agent.agents a ON a.id = t.agent_id WHERE " + ENDED
            + " GROUP BY t.agent_id, a.name, a.archived_at ORDER BY cost_usd DESC, runs DESC, a.name";

    private static final String TOP_ISSUES = "SELECT t.issue_id, " + SUMS + " FROM agent.agent_tasks t WHERE " + ENDED
            + " GROUP BY t.issue_id ORDER BY cost_usd DESC, (COALESCE(sum(t.input_tokens), 0) + COALESCE(sum(t.output_tokens), 0)) DESC,"
            + " runs DESC LIMIT :limit";

    private static final String BY_DAY = "SELECT (t.finished_at AT TIME ZONE 'UTC')::date AS day, " + SUMS
            + " FROM agent.agent_tasks t WHERE " + ENDED + " GROUP BY day ORDER BY day";

    private final JdbcClient jdbc;

    UsageReaderAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Totals totals(UUID workspaceId, Instant from) {
        return jdbc.sql(TOTALS)
                .param("workspaceId", workspaceId)
                .param("from", utc(from))
                .query((rs, row) -> totals(rs))
                .single();
    }

    @Override
    public List<AgentTotals> byAgent(UUID workspaceId, Instant from) {
        return jdbc.sql(BY_AGENT)
                .param("workspaceId", workspaceId)
                .param("from", utc(from))
                .query((rs, row) -> new AgentTotals(
                        rs.getObject("agent_id", UUID.class), rs.getString("name"), rs.getBoolean("archived"), totals(rs)))
                .list();
    }

    @Override
    public List<IssueTotals> topIssues(UUID workspaceId, Instant from, int limit) {
        return jdbc.sql(TOP_ISSUES)
                .param("workspaceId", workspaceId)
                .param("from", utc(from))
                .param("limit", limit)
                .query((rs, row) -> new IssueTotals(rs.getObject("issue_id", UUID.class), totals(rs)))
                .list();
    }

    @Override
    public List<DayTotals> byDay(UUID workspaceId, Instant from) {
        return jdbc.sql(BY_DAY)
                .param("workspaceId", workspaceId)
                .param("from", utc(from))
                .query((rs, row) -> new DayTotals(rs.getObject("day", LocalDate.class), totals(rs)))
                .list();
    }

    private static Totals totals(ResultSet rs) throws SQLException {
        BigDecimal cost = rs.getBigDecimal("cost_usd");
        return new Totals(
                rs.getLong("runs"),
                rs.getLong("completed"),
                rs.getLong("failed"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                cost == null ? BigDecimal.ZERO : cost);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
