package com.lifeos.platform.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * One database, six schemas, six migration histories.
 *
 * The microservice edition gave each service its own database, which meant three of
 * them could each own a table called {@code user_settings} without anyone noticing.
 * Merging them into one database keeps that fact — a Postgres schema per bounded
 * context — rather than renaming tables, so every migration file is byte-identical
 * to the one it replaced and the entities say which schema they live in.
 *
 * Each schema gets its own {@code flyway_schema_history}, so a context can be
 * migrated, repaired or (in development) dropped on its own.
 *
 * Replacing the migration strategy rather than adding a runner is what keeps the
 * ordering right: Spring Boot already makes the {@code EntityManagerFactory} depend
 * on the Flyway initialiser, so Hibernate's schema validation cannot run first.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /** Migration order matters only for readability; no schema references another. */
    public static final List<String> SCHEMAS =
            List.of("auth", "habit", "expense", "planning", "notification", "analytics");

    @Bean
    public FlywayMigrationStrategy multiSchemaMigrationStrategy(DataSource dataSource) {
        return autoConfigured -> {
            enableExtensions(dataSource);

            for (String schema : SCHEMAS) {
                var result = Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration/" + schema)
                        .table("flyway_schema_history")
                        .baselineOnMigrate(true)
                        .load()
                        .migrate();

                log.info("Schema '{}': {} migration(s) applied, now at version {}",
                        schema, result.migrationsExecuted,
                        result.targetSchemaVersion == null ? "empty" : result.targetSchemaVersion);
            }
        };
    }

    /**
     * {@code pg_trgm} backs the substring search over transaction notes and merchant
     * names. A managed Postgres may refuse to install it, which is why this logs
     * rather than throws — the migration that wants it checks for it and skips the
     * index, so search still works, just with a sequential scan.
     */
    private void enableExtensions(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        } catch (Exception ex) {
            log.warn("Could not enable pg_trgm ({}). Text search will fall back to a scan.",
                    ex.getMessage());
        }
    }
}
