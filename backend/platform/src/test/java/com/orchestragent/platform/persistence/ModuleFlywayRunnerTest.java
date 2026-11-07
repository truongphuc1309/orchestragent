package com.orchestragent.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ModuleFlywayRunnerTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Test
    void runsModulesInOrderEachInItsOwnSchemaWithItsOwnHistoryTable() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        // Declared out of order on purpose: "second" must still run after "first".
        ModuleFlywayRunner runner = new ModuleFlywayRunner(dataSource, List.of(
                new ModuleMigration("second", "classpath:db/test-migration/second", 20),
                new ModuleMigration("first", "classpath:db/test-migration/first", 10)));

        runner.afterPropertiesSet();

        assertThat(runner.migrations()).extracting(ModuleMigration::schema).containsExactly("first", "second");
        try (Connection connection = dataSource.getConnection()) {
            assertThat(exists(connection, "first", "things")).isTrue();
            assertThat(exists(connection, "second", "other_things")).isTrue();
            assertThat(exists(connection, "first", "flyway_schema_history")).isTrue();
            assertThat(exists(connection, "second", "flyway_schema_history")).isTrue();
            assertThat(exists(connection, "public", "things")).isFalse();
        }
    }

    private static boolean exists(Connection connection, String schema, String table) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }
}
