package com.lifeos;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A real Postgres for the tests, without Docker.
 *
 * An in-memory database in "Postgres compatibility mode" cannot honestly stand in
 * here: the migrations use {@code jsonb}, GIN indexes, partial indexes, {@code md5()}
 * in a unique index, and — most importantly — six schemas. A test that skipped those
 * would pass on a schema the deployed application never runs.
 *
 * One instance is started for the whole JVM and shared: booting Postgres costs a
 * couple of seconds, and every test wants the same empty database.
 */
public abstract class PostgresBackedTest {

    protected static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start the embedded Postgres", ex);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                POSTGRES.close();
            } catch (IOException ignored) {
                // Shutting down; a leaked temp directory is not worth failing over.
            }
        }));
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }
}
