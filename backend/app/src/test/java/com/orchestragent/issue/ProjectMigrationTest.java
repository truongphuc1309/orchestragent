package com.orchestragent.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.testsupport.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Step 13 moved issues into projects. These tests run the real migration files on data written in the shape that
 * existed before (in scratch schemas, so the application's own schemas are untouched): old issues must land in a
 * "General" project with the key GEN, keep their numbers, and the project must carry on counting.
 */
class ProjectMigrationTest extends IntegrationTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T10:00:00Z");

    @Autowired
    private DataSource dataSource;

    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final String issueSchema = "mig_issue_" + suffix;
    private final String inboxSchema = "mig_inbox_" + suffix;

    @AfterEach
    void dropScratchSchemas() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + issueSchema + " CASCADE").update();
        jdbc.sql("DROP SCHEMA IF EXISTS " + inboxSchema + " CASCADE").update();
    }

    private Flyway flyway(String schema, String location, String target) {
        var config = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(location);
        return (target == null ? config : config.target(target)).load();
    }

    private void oldIssue(UUID workspace, int number, String creatorType, UUID creator, Instant createdAt) {
        jdbc.sql("INSERT INTO " + issueSchema + ".issues (id, workspace_id, number, title, description, status, priority, "
                        + "creator_type, creator_id, position, created_at, updated_at) "
                        + "VALUES (:id, :ws, :number, :title, '', 'todo', 'none', :creatorType, :creator, 1024, :at, :at)")
                .param("id", UUID.randomUUID())
                .param("ws", workspace)
                .param("number", number)
                .param("title", "Issue " + number)
                .param("creatorType", creatorType)
                .param("creator", creator)
                .param("at", Timestamp.from(createdAt))
                .update();
    }

    @Test
    void oldIssuesMoveToAGeneralProjectPerWorkspaceAndKeepTheirNumbers() {
        flyway(issueSchema, "classpath:db/migration/issue", "2").migrate();
        UUID busy = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        UUID agent = UUID.randomUUID();
        // An agent created the first issue of "busy"; the project is still credited to a person.
        oldIssue(busy, 1, "agent", agent, T1);
        oldIssue(busy, 2, "member", person, T2);
        oldIssue(busy, 4, "member", UUID.randomUUID(), T3); // issue 3 was deleted; the counter is at 5
        jdbc.sql("INSERT INTO " + issueSchema + ".issue_counters (workspace_id, next_number) VALUES (:ws, 5)")
                .param("ws", busy)
                .update();
        oldIssue(quiet, 1, "member", person, T2); // no counter row: the project counts on from the highest number

        flyway(issueSchema, "classpath:db/migration/issue", null).migrate();

        List<Map<String, Object>> projects = jdbc.sql("SELECT workspace_id, key, name, created_by, next_number, created_at, "
                        + "archived_at FROM " + issueSchema + ".projects ORDER BY next_number DESC")
                .query()
                .listOfRows();
        assertThat(projects).hasSize(2);
        assertThat(projects.get(0))
                .containsEntry("workspace_id", busy)
                .containsEntry("key", "GEN")
                .containsEntry("name", "General")
                .containsEntry("created_by", person)
                .containsEntry("next_number", 5)
                .containsEntry("archived_at", null);
        assertThat(projects.get(1))
                .containsEntry("workspace_id", quiet)
                .containsEntry("key", "GEN")
                .containsEntry("next_number", 2);

        List<Map<String, Object>> issues = jdbc.sql("SELECT i.number, i.project_key, p.workspace_id FROM " + issueSchema
                        + ".issues i JOIN " + issueSchema + ".projects p ON p.id = i.project_id ORDER BY p.workspace_id, i.number")
                .query()
                .listOfRows();
        assertThat(issues).hasSize(4).allSatisfy(row -> assertThat(row).containsEntry("project_key", "GEN"));
        assertThat(jdbc.sql("SELECT count(*) FROM " + issueSchema + ".issues WHERE project_id IS NULL").query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("SELECT to_regclass(:name)::text").param("name", issueSchema + ".issue_counters")
                        .query(String.class).optional())
                .as("the workspace counter is gone")
                .isEmpty();
    }

    @Test
    void anEmptyIssueSchemaMigratesWithoutProjects() {
        flyway(issueSchema, "classpath:db/migration/issue", null).migrate();

        assertThat(jdbc.sql("SELECT count(*) FROM " + issueSchema + ".projects").query(Long.class).single()).isZero();
    }

    @Test
    void oldNotificationsGetTheGeneralIssueKey() {
        flyway(inboxSchema, "classpath:db/migration/inbox", "1").migrate();
        jdbc.sql("INSERT INTO " + inboxSchema + ".notifications (id, workspace_id, recipient_user_id, type, issue_id, "
                        + "issue_number, issue_title, actor_name, source_event_id, created_at) "
                        + "VALUES (:id, :ws, :user, 'issue_assigned', :issue, 7, 'Fix it', 'Mia', :event, now())")
                .param("id", UUID.randomUUID())
                .param("ws", UUID.randomUUID())
                .param("user", UUID.randomUUID())
                .param("issue", UUID.randomUUID())
                .param("event", UUID.randomUUID())
                .update();

        flyway(inboxSchema, "classpath:db/migration/inbox", null).migrate();

        assertThat(jdbc.sql("SELECT issue_key FROM " + inboxSchema + ".notifications").query(String.class).single())
                .isEqualTo("GEN-7");
        assertThat(jdbc.sql("SELECT count(*) FROM information_schema.columns WHERE table_schema = :schema "
                        + "AND table_name = 'notifications' AND column_name = 'issue_number'")
                .param("schema", inboxSchema)
                .query(Long.class)
                .single())
                .isZero();
    }

    @Test
    void theDatabaseKeepsTheCopiedKeyTrueAndKeysUnique() {
        UUID workspace = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        jdbc.sql("INSERT INTO issue.projects (id, workspace_id, key, name, description, created_by, created_at, updated_at) "
                        + "VALUES (:id, :ws, 'DBX', 'Db', '', :user, now(), now())")
                .param("id", project)
                .param("ws", workspace)
                .param("user", UUID.randomUUID())
                .update();

        assertRejected("INSERT INTO issue.projects (id, workspace_id, key, name, description, created_by, created_at, "
                + "updated_at) VALUES (gen_random_uuid(), '" + workspace + "', 'DBX', 'Again', '', gen_random_uuid(), now(), now())");
        assertRejected("INSERT INTO issue.projects (id, workspace_id, key, name, description, created_by, created_at, "
                + "updated_at) VALUES (gen_random_uuid(), '" + workspace + "', 'dbx2', 'Lower', '', gen_random_uuid(), now(), now())");
        String issue = "INSERT INTO issue.issues (id, workspace_id, project_id, project_key, number, title, description, "
                + "status, priority, creator_type, creator_id, position, created_at, updated_at) VALUES (gen_random_uuid(), "
                + "'%s', '" + project + "', '%s', 1, 't', '', 'todo', 'none', 'member', gen_random_uuid(), 1, now(), now())";
        assertRejected(issue.formatted(workspace, "OTHER"));
        assertRejected(issue.formatted(UUID.randomUUID(), "DBX"));
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
