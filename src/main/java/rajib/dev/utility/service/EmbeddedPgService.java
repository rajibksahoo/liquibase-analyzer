package rajib.dev.utility.service;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class EmbeddedPgService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPgService.class);

    private EmbeddedPostgres embeddedPostgres;
    private DataSource dataSource;
    private boolean running = false;

    public synchronized void start() throws IOException {
        if (running) {
            log.info("Embedded PostgreSQL is already running");
            return;
        }
        log.info("Starting embedded PostgreSQL...");
        embeddedPostgres = EmbeddedPostgres.start();
        dataSource = embeddedPostgres.getPostgresDatabase();
        running = true;
        log.info("Embedded PostgreSQL started on port {}", embeddedPostgres.getPort());
    }

    public synchronized DataSource getDataSource() {
        if (!running) {
            throw new IllegalStateException("Embedded PostgreSQL is not running. Call start() first.");
        }
        return dataSource;
    }

    public synchronized Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int getPort() {
        if (!running) return -1;
        return embeddedPostgres.getPort();
    }

    public synchronized void resetDatabase() throws SQLException {
        if (!running) return;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA public CASCADE");
            stmt.execute("CREATE SCHEMA public");
            stmt.execute("GRANT ALL ON SCHEMA public TO public");
            // Create as_admin with SUPERUSER so SET ROLE changesets have full privileges
            stmt.execute("CREATE ROLE IF NOT EXISTS as_admin SUPERUSER");
            stmt.execute("ALTER ROLE as_admin SUPERUSER");
        }
        log.info("Embedded PostgreSQL database reset (role as_admin ensured as SUPERUSER)");
    }

    /**
     * Called after Liquibase execution to ensure the two tracking tables are
     * owned by the postgres superuser and that as_admin has full privileges on
     * every object in the public schema.
     */
    public synchronized void grantLiquibaseTablePrivileges() throws SQLException {
        if (!running) return;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE IF EXISTS public.databasechangelog     OWNER TO postgres");
            stmt.execute("ALTER TABLE IF EXISTS public.databasechangeloglock OWNER TO postgres");
            stmt.execute("GRANT ALL PRIVILEGES ON SCHEMA public                    TO as_admin");
            stmt.execute("GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public   TO as_admin");
            stmt.execute("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public   TO as_admin");
            stmt.execute("GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public   TO as_admin");
        }
        log.info("Liquibase table ownership set to postgres; full privileges granted to as_admin");
    }

    @PreDestroy
    public synchronized void stop() {
        if (running && embeddedPostgres != null) {
            try {
                log.info("Stopping embedded PostgreSQL...");
                embeddedPostgres.close();
                running = false;
                log.info("Embedded PostgreSQL stopped");
            } catch (IOException e) {
                log.error("Error stopping embedded PostgreSQL", e);
            }
        }
    }
}
