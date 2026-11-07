package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import com.orchestragent.issue.domain.IssueNumbers;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IssueNumbersAdapter implements IssueNumbers {

    /**
     * One statement both creates the counter and increments it, and the row lock it takes makes concurrent
     * callers wait for each other, so two issues never get the same number. The first call inserts 2 and
     * returns 1; later calls return the number before the increment.
     */
    private static final String NEXT = """
            INSERT INTO issue.issue_counters (workspace_id, next_number) VALUES (:workspaceId, 2)
            ON CONFLICT (workspace_id) DO UPDATE SET next_number = issue.issue_counters.next_number + 1
            RETURNING next_number - 1
            """;

    private final JdbcClient jdbc;

    IssueNumbersAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int next(UUID workspaceId) {
        return jdbc.sql(NEXT).param("workspaceId", workspaceId).query(Integer.class).single();
    }
}
