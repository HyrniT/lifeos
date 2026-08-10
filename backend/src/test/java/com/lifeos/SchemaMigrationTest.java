package com.lifeos;

import com.lifeos.platform.config.FlywayConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that would have caught the merge going wrong.
 *
 * Booting the context at all proves three things that are easy to get wrong when
 * six deployments become one:
 *
 *  - every schema migrates, in one database, each with its own history table;
 *  - {@code ddl-auto: validate} matches every entity against the schema it declares,
 *    so a {@code @Table(schema = ...)} that points at the wrong place fails here
 *    rather than on the first query in production;
 *  - no two beans collide — three classes called {@code UserSettings}, six
 *    configurations that each used to define an {@code OpenAPI} bean.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Six schemas migrate into one database")
class SchemaMigrationTest extends PostgresBackedTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("every bounded context has its own schema and its own migration history")
    void schemasExist() {
        List<String> schemas = jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).containsAll(FlywayConfig.SCHEMAS);

        for (String schema : FlywayConfig.SCHEMAS) {
            Integer applied = jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".flyway_schema_history WHERE success", Integer.class);
            assertThat(applied)
                    .as("migrations applied in schema '%s'", schema)
                    .isNotNull()
                    .isPositive();
        }
    }

    @Test
    @DisplayName("the three user_settings tables coexist, one per context")
    void collidingTablesAreSeparated() {
        List<String> owners = jdbc.queryForList("""
                SELECT table_schema FROM information_schema.tables
                WHERE table_name = 'user_settings' ORDER BY table_schema
                """, String.class);

        // The reason the schemas exist at all: in one shared schema, whichever
        // migration ran second would have failed on a duplicate table.
        assertThat(owners).containsExactly("habit", "notification", "planning");
    }

    @Test
    @DisplayName("the analytics read models are Postgres tables now, not Mongo collections")
    void analyticsTablesExist() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'analytics'
                """, String.class);

        assertThat(tables).contains("daily_rollup", "daily_rollup_category", "event_record");
    }
}
