package rajib.dev.utility.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import rajib.dev.utility.dto.ChangelogInspectResponse;
import rajib.dev.utility.dto.LiquibaseExecutionResponse;
import rajib.dev.utility.entity.SchemaSnapshotEntity;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.service.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/liquibase")
@Tag(name = "Liquibase Execution")
public class LiquibaseController {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseController.class);

    private final ChangelogExtractorService extractorService;
    private final ChangelogValidatorService validatorService;
    private final UploadSessionService uploadSessionService;
    private final LiquibaseExecutionService executionService;
    private final SchemaIntrospectionService introspectionService;
    private final SchemaSnapshotService snapshotService;
    private final EmbeddedPgService embeddedPgService;
    private final ObjectMapper objectMapper;

    public LiquibaseController(ChangelogExtractorService extractorService,
                               ChangelogValidatorService validatorService,
                               UploadSessionService uploadSessionService,
                               LiquibaseExecutionService executionService,
                               SchemaIntrospectionService introspectionService,
                               SchemaSnapshotService snapshotService,
                               EmbeddedPgService embeddedPgService,
                               ObjectMapper objectMapper) {
        this.extractorService    = extractorService;
        this.validatorService    = validatorService;
        this.uploadSessionService = uploadSessionService;
        this.executionService    = executionService;
        this.introspectionService = introspectionService;
        this.snapshotService     = snapshotService;
        this.embeddedPgService   = embeddedPgService;
        this.objectMapper        = objectMapper;
    }

    @Operation(summary = "Execute a Liquibase changelog ZIP against embedded or external DB")
    @PostMapping("/execute")
    public ResponseEntity<LiquibaseExecutionResponse> execute(
            @RequestParam(value = "file",           required = false) MultipartFile file,
            @RequestParam(value = "uploadToken",    required = false) String uploadToken,
            @RequestParam(value = "mode",           defaultValue = "embedded") String mode,
            @RequestParam(value = "snapshotName",   required = false) String snapshotName,
            @RequestParam(value = "externalUrl",    required = false) String externalUrl,
            @RequestParam(value = "externalUser",   required = false) String externalUser,
            @RequestParam(value = "externalPassword", required = false) String externalPassword,
            @RequestParam(value = "properties",     required = false) String propertiesJson) {

        try {
            // Resolve the master changelog path from token or direct upload
            Path masterChangelog;
            String sourceName;
            if (uploadToken != null && !uploadToken.isBlank()) {
                masterChangelog = uploadSessionService.consume(uploadToken);
                sourceName = masterChangelog.getParent().getFileName().toString();
            } else if (file != null && !file.isEmpty()) {
                Path extractDir = extractorService.extractZip(file);
                masterChangelog = extractorService.findMasterChangelog(extractDir);
                sourceName = file.getOriginalFilename();
            } else {
                return ResponseEntity.badRequest().body(
                        new LiquibaseExecutionResponse(false,
                                "Either 'file' or 'uploadToken' must be provided.", null));
            }

            // Parse user-provided property values
            Map<String, String> userProperties = new HashMap<>();
            if (propertiesJson != null && !propertiesJson.isBlank()) {
                userProperties = objectMapper.readValue(
                        propertiesJson, new TypeReference<Map<String, String>>() {});
            }

            Connection connection = executionService.executeChangelog(
                    masterChangelog, mode, externalUrl, externalUser, externalPassword,
                    userProperties);

            String name = (snapshotName != null && !snapshotName.isBlank()) ? snapshotName : sourceName;
            SchemaSnapshot snapshot = introspectionService.introspect(connection, "public", name);
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

    @Operation(summary = "Get embedded PostgreSQL status")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "embeddedPgRunning", embeddedPgService.isRunning(),
                "embeddedPgPort",    embeddedPgService.getPort()
        ));
    }
}
