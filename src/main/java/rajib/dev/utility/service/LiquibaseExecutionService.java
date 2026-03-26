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
    private final ChangelogValidatorService changelogValidatorService;

    public LiquibaseExecutionService(EmbeddedPgService embeddedPgService,
                                     ChangelogValidatorService changelogValidatorService) {
        this.embeddedPgService = embeddedPgService;
        this.changelogValidatorService = changelogValidatorService;
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

        // Validate property expressions upfront to prevent StackOverflowError in
        // ExpressionExpander.expandExpressions() caused by circular ${param} references.
        changelogValidatorService.validatePropertyExpressions(masterChangelog);

        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));

        try (Liquibase liquibase = new Liquibase(
                changelogFile,
                new DirectoryResourceAccessor(changelogDir),
                database)) {
            try {
                liquibase.update("");
            } catch (StackOverflowError e) {
                // Safety net: if the validator missed a deeply nested expansion path,
                // convert the unrecoverable error into a descriptive exception.
                throw new IllegalStateException(
                        "StackOverflowError in Liquibase ExpressionExpander: the changelog "
                        + "contains a property expression that expands infinitely. "
                        + "Check for circular or self-referential ${param} definitions.", e);
            }
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
