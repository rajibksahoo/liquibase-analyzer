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
import java.util.Map;

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

    /**
     * Executes the Liquibase changelog, injecting {@code userProperties} into
     * {@code ChangeLogParameters} before {@code update()} so that self-referential
     * expressions like {@code ${service.schema.name}} expand to the user-supplied value
     * instead of recursing infinitely.
     *
     * @param userProperties values entered by the user for unresolved changelog properties;
     *                       may be empty when the changelog has no parameter placeholders
     */
    public Connection executeChangelog(Path masterChangelog, String mode,
                                       String externalUrl, String externalUser, String externalPassword,
                                       Map<String, String> userProperties) throws Exception {

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

        Path changelogDir  = masterChangelog.getParent();
        String changelogFile = masterChangelog.getFileName().toString();
        log.info("Executing Liquibase changelog: {} from directory: {}", changelogFile, changelogDir);

        // Validate with user values applied; prevents StackOverflowError from any
        // remaining circular chains that the user did not resolve.
        changelogValidatorService.validatePropertyExpressions(masterChangelog, userProperties);

        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));

        try (Liquibase liquibase = new Liquibase(
                changelogFile,
                new DirectoryResourceAccessor(changelogDir),
                database)) {

            // Inject user-provided values before update() so that self-referential
            // ${param} expressions in the changelog expand to the real value instead
            // of triggering infinite recursion in ExpressionExpander.
            if (!userProperties.isEmpty()) {
                log.info("Injecting {} user-provided changelog properties", userProperties.size());
                userProperties.forEach((name, value) ->
                        liquibase.getChangeLogParameters().set(name, value));
            }

            try {
                liquibase.update("");
            } catch (StackOverflowError e) {
                // Safety net for expansion paths the static validator cannot reach.
                throw new IllegalStateException(
                        "StackOverflowError in Liquibase ExpressionExpander: the changelog "
                        + "contains a property expression that expands infinitely. "
                        + "Check for circular or self-referential ${param} definitions.", e);
            }
        }

        log.info("Liquibase changelog executed successfully");

        // Return a fresh connection for introspection (the Liquibase one may be closed).
        if ("external".equalsIgnoreCase(mode) && externalUrl != null && !externalUrl.isBlank()) {
            return DriverManager.getConnection(externalUrl, externalUser, externalPassword);
        } else {
            return embeddedPgService.getConnection();
        }
    }
}
