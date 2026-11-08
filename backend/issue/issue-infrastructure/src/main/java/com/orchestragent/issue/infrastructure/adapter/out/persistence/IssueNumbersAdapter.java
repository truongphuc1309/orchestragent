package com.orchestragent.issue.infrastructure.adapter.out.persistence;

import com.orchestragent.issue.domain.IssueNumbers;
import com.orchestragent.issue.domain.ProjectId;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IssueNumbersAdapter implements IssueNumbers {

    /**
     * One statement increments the counter of the project and returns the number before the increment. The row
     * lock it takes makes concurrent callers wait for each other, so two issues never get the same number. An
     * archived project (or one of another workspace) matches no row, so nothing can be added to it even when it
     * was archived after the caller checked.
     */
    private static final String NEXT = """
            UPDATE issue.projects SET next_number = next_number + 1
            WHERE id = :projectId AND workspace_id = :workspaceId AND archived_at IS NULL
            RETURNING next_number - 1
            """;

    private final JdbcClient jdbc;

    IssueNumbersAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt next(UUID workspaceId, ProjectId projectId) {
        return jdbc.sql(NEXT)
                .param("projectId", projectId.value())
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .optional()
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }
}
