package rajib.dev.utility.service;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

@Service
public class LiquibaseExecutionService {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseExecutionService.class);

    private final EmbeddedPgService embeddedPgService;

    public LiquibaseExecutionService(EmbeddedPgService embeddedPgService) {
        this.embeddedPgService = embeddedPgService;
    }

    public Connection executeChangelog(Path masterChangelog, String mode,
                                       String externalUrl, String externalUser, String externalPassword)
            throws Exception {

        Connection connection;

        if ("external".equalsIgnoreCase(mode) && externalUrl != null && !externalUrl.isBlank()) {
            log.info("Using external PostgreSQL: {}", externalUrl);
            connection = DriverManager.getConnection(externalUrl, externalUser, externalPassword);
        } else {
            log.info("Using embedded PostgreSQL");
            embeddedPgService.start();
            embeddedPgService.resetDatabase();
            connection = embeddedPgService.getConnection();
        }

        Path changelogDir = masterChangelog.getParent();
        String changelogFile = masterChangelog.getFileName().toString();

        log.info("Executing Liquibase changelog: {} from directory: {}", changelogFile, changelogDir);

        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));

        try (Liquibase liquibase = new Liquibase(
                changelogFile,
                new DirectoryResourceAccessor(changelogDir),
                database)) {
            liquibase.update("");
        }

        log.info("Liquibase changelog executed successfully");

        // Return a fresh connection for introspection (the Liquibase one may be in odd state)
        if ("external".equalsIgnoreCase(mode) && externalUrl != null && !externalUrl.isBlank()) {
            return DriverManager.getConnection(externalUrl, externalUser, externalPassword);
        } else {
            return embeddedPgService.getConnection();
        }
    }
}
