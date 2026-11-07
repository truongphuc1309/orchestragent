package com.orchestragent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.orchestragent.testsupport.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AGENTS.md: contexts own separate schemas, with no foreign keys between them (only plain UUIDs). */
class SchemaIsolationTest extends IntegrationTest {

    @Test
    void noForeignKeyCrossesASchemaBoundary() {
        List<String> crossing = jdbc.sql("""
                        SELECT n1.nspname || '.' || c1.relname || ' -> ' || n2.nspname || '.' || c2.relname
                        FROM pg_constraint con
                        JOIN pg_class c1 ON c1.oid = con.conrelid
                        JOIN pg_namespace n1 ON n1.oid = c1.relnamespace
                        JOIN pg_class c2 ON c2.oid = con.confrelid
                        JOIN pg_namespace n2 ON n2.oid = c2.relnamespace
                        WHERE con.contype = 'f' AND n1.nspname <> n2.nspname
                        """)
                .query(String.class)
                .list();

        assertThat(crossing).isEmpty();
    }

    @Test
    void everyContextHasItsOwnSchemaWithItsOwnFlywayHistory() {
        for (String schema : List.of("identity", "workspace", "issue", "agent", "inbox")) {
            Integer history = jdbc.sql("SELECT count(*) FROM information_schema.tables "
                            + "WHERE table_schema = :schema AND table_name = 'flyway_schema_history'")
                    .param("schema", schema)
                    .query(Integer.class)
                    .single();
            assertThat(history).as("flyway history of schema %s", schema).isEqualTo(1);
        }
    }
}
