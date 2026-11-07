package com.orchestragent.platform.persistence;

import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Runs the migrations of every module, one after another, each in its own schema with its own history
 * table. Spring Boot's Flyway auto-configuration is disabled ({@code spring.flyway.enabled=false}).
 */
public class ModuleFlywayRunner implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ModuleFlywayRunner.class);

    private final DataSource dataSource;
    private final List<ModuleMigration> migrations;

    public ModuleFlywayRunner(DataSource dataSource, List<ModuleMigration> migrations) {
        this.dataSource = dataSource;
        this.migrations = migrations.stream().sorted(Comparator.comparingInt(ModuleMigration::order)).toList();
    }

    @Override
    public void afterPropertiesSet() {
        migrations.forEach(this::migrate);
    }

    private void migrate(ModuleMigration migration) {
        log.info("Migrating schema '{}' from {}", migration.schema(), migration.location());
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(migration.schema())
                .defaultSchema(migration.schema())
                .createSchemas(true)
                .locations(migration.location())
                .load()
                .migrate();
    }

    /** Migrations in the order they run. */
    public List<ModuleMigration> migrations() {
        return migrations;
    }
}
