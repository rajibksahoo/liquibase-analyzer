package rajib.dev.utility.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import rajib.dev.utility.dto.LiquibaseExecutionResponse;
import rajib.dev.utility.entity.SchemaSnapshotEntity;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.service.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/liquibase")
public class LiquibaseController {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseController.class);

    private final ChangelogExtractorService extractorService;
    private final LiquibaseExecutionService executionService;
    private final SchemaIntrospectionService introspectionService;
    private final SchemaSnapshotService snapshotService;
    private final EmbeddedPgService embeddedPgService;

    public LiquibaseController(ChangelogExtractorService extractorService,
                               LiquibaseExecutionService executionService,
                               SchemaIntrospectionService introspectionService,
                               SchemaSnapshotService snapshotService,
                               EmbeddedPgService embeddedPgService) {
        this.extractorService = extractorService;
        this.executionService = executionService;
        this.introspectionService = introspectionService;
        this.snapshotService = snapshotService;
        this.embeddedPgService = embeddedPgService;
    }

    @PostMapping("/execute")
    public ResponseEntity<LiquibaseExecutionResponse> execute(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "embedded") String mode,
            @RequestParam(value = "snapshotName", required = false) String snapshotName,
            @RequestParam(value = "externalUrl", required = false) String externalUrl,
            @RequestParam(value = "externalUser", required = false) String externalUser,
            @RequestParam(value = "externalPassword", required = false) String externalPassword) {

        try {
            Path extractDir = extractorService.extractZip(file);
            Path masterChangelog = extractorService.findMasterChangelog(extractDir);

            Connection connection = executionService.executeChangelog(
                    masterChangelog, mode, externalUrl, externalUser, externalPassword);

            SchemaSnapshot snapshot = introspectionService.introspect(
                    connection, "public",
                    snapshotName != null ? snapshotName : file.getOriginalFilename());
            connection.close();

            SchemaSnapshotEntity saved = snapshotService.save(snapshot);

            return ResponseEntity.ok(new LiquibaseExecutionResponse(
                    true,
                    "Changelog executed successfully. " + snapshot.tables().size() + " tables found.",
                    saved.getId()));

        } catch (Exception e) {
            log.error("Liquibase execution failed", e);
            return ResponseEntity.badRequest().body(
                    new LiquibaseExecutionResponse(false, "Execution failed: " + e.getMessage(), null));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "embeddedPgRunning", embeddedPgService.isRunning(),
                "embeddedPgPort", embeddedPgService.getPort()
        ));
    }
}
