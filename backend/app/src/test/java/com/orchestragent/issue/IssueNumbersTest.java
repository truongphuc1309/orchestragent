package com.orchestragent.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.issue.domain.IssueNumbers;
import com.orchestragent.issue.domain.ProjectId;
import com.orchestragent.testsupport.IntegrationTest;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The counter itself, on Postgres. The service refuses an archived project before it asks for a number, so this is
 * the only place that shows the counter refuses one too (a project archived between the check and the insert).
 */
class IssueNumbersTest extends IntegrationTest {

    @Autowired
    private IssueNumbers numbers;

    private UUID project(UUID workspace, String key) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO issue.projects (id, workspace_id, key, name, description, created_by, created_at, updated_at) "
                        + "VALUES (:id, :ws, :key, 'P', '', :user, now(), now())")
                .param("id", id)
                .param("ws", workspace)
                .param("key", key)
                .param("user", UUID.randomUUID())
                .update();
        return id;
    }

    @Test
    void countsPerProjectFromOne() {
        UUID workspace = UUID.randomUUID();
        ProjectId web = new ProjectId(project(workspace, "WEB"));
        ProjectId api = new ProjectId(project(workspace, "API"));

        assertThat(numbers.next(workspace, web)).isEqualTo(OptionalInt.of(1));
        assertThat(numbers.next(workspace, web)).isEqualTo(OptionalInt.of(2));
        assertThat(numbers.next(workspace, api)).isEqualTo(OptionalInt.of(1));
        assertThat(numbers.next(workspace, web)).isEqualTo(OptionalInt.of(3));
    }

    @Test
    void handsOutNothingForAnArchivedProjectOrOneOfAnotherWorkspace() {
        UUID workspace = UUID.randomUUID();
        UUID archived = project(workspace, "OLD");
        jdbc.sql("UPDATE issue.projects SET archived_at = now() WHERE id = :id").param("id", archived).update();
        ProjectId other = new ProjectId(project(UUID.randomUUID(), "WEB"));

        assertThat(numbers.next(workspace, new ProjectId(archived))).isEmpty();
        assertThat(numbers.next(workspace, other)).isEmpty();
        assertThat(numbers.next(workspace, new ProjectId(UUID.randomUUID()))).isEmpty();
    }
}
