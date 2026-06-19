package com.example.codereview.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.util.StreamUtils;

/**
 * Proves the Flyway migrations upgrade an older, non-empty database without data loss:
 * load the pre-V2 legacy schema, run Flyway with baseline-on-migrate, then assert the new
 * columns, the idempotency index, and the feedback de-duplication all took effect.
 * Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayLegacyUpgradeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("code_review")
                    .withUsername("code_review")
                    .withPassword("test");

    private static DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    @Test
    void legacyDatabaseUpgradesCleanly() throws Exception {
        DataSource ds = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // Seed the legacy (pre-V2) schema and data.
        try (InputStream in = getClass().getResourceAsStream("/db/legacy-schema.sql")) {
            String legacy = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            jdbc.execute(legacy);
        }
        assertThat(jdbc.queryForObject("select count(*) from feedback", Integer.class)).isEqualTo(2);

        // Run the real migrations against the non-empty legacy database.
        MigrateResult result = Flyway.configure()
                .dataSource(ds)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);

        // New columns exist and were back-filled.
        jdbc.queryForObject("select base_commit_id_normalized from review_task limit 1", String.class);
        jdbc.queryForObject("select total_tokens from ai_call_log limit 1", Integer.class);
        jdbc.queryForObject("select session_version from user_account limit 1", Integer.class);

        // Duplicate feedback row removed before the unique index was enforced.
        assertThat(jdbc.queryForObject("select count(*) from feedback", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where indexname = 'uq_review_task_idempotency'",
                Integer.class)).isEqualTo(1);

        // Re-running is a no-op (idempotent).
        MigrateResult rerun = Flyway.configure()
                .dataSource(ds)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertThat(rerun.migrationsExecuted).isEqualTo(0);
    }
}
