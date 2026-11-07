package com.orchestragent.agent.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sums of the runs of a workspace that ended at or after a moment (completed, failed or cancelled). An output port:
 * the queries run in the database ({@code agent.agent_tasks}). Tokens and cost a run did not report count as zero.
 */
public interface UsageReader {

    /** Runs, how they ended, tokens and cost. */
    record Totals(long runs, long completed, long failed, long inputTokens, long outputTokens, BigDecimal costUsd) {

        public static final Totals NONE = new Totals(0, 0, 0, 0, 0, BigDecimal.ZERO);
    }

    record AgentTotals(UUID agentId, String agentName, boolean archived, Totals totals) {
    }

    record IssueTotals(UUID issueId, Totals totals) {
    }

    /** {@code day} is the UTC date on which the runs ended. */
    record DayTotals(LocalDate day, Totals totals) {
    }

    Totals totals(UUID workspaceId, Instant from);

    /** Every agent that has runs, most expensive first. */
    List<AgentTotals> byAgent(UUID workspaceId, Instant from);

    /** The issues with the most expensive runs (then the most tokens), at most {@code limit}. */
    List<IssueTotals> topIssues(UUID workspaceId, Instant from, int limit);

    /** Only days with runs, oldest first. */
    List<DayTotals> byDay(UUID workspaceId, Instant from);
}
