package com.orchestragent.platform.persistence;

/**
 * Declares the Flyway migrations of one module. Every {@code *-infrastructure} module (and the platform
 * itself) contributes one bean of this type.
 *
 * @param schema   Postgres schema owned by the module; it also holds the module's own Flyway history table
 * @param location Flyway location, for example {@code classpath:db/migration/identity}
 * @param order    execution order across modules; lower runs first
 */
public record ModuleMigration(String schema, String location, int order) {

    public ModuleMigration {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }
}
