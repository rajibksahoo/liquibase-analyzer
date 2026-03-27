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
        this.extractorService     = extractorService;
        this.validatorService     = validatorService;
        this.uploadSessionService = uploadSessionService;
        this.executionService     = executionService;
        this.introspectionService = introspectionService;
        this.snapshotService      = snapshotService;
        this.embeddedPgService    = embeddedPgService;
        this.objectMapper         = objectMapper;
    }

    /**
     * Step 1 – Upload and inspect the changelog ZIP.
     * <p>Returns all {@code <databaseChangeLog>} XML files found in the ZIP (sorted by
     * master-likelihood) so the UI can present a dropdown, plus the unresolved
     * {@code ${param}} properties for the auto-selected best candidate.
     */
    @Operation(summary = "Inspect a changelog ZIP: discover changelog files and unresolved properties")
    @PostMapping("/inspect")
    public ResponseEntity<ChangelogInspectResponse> inspect(
            @RequestParam("file") MultipartFile file) {
        try {
            Path extractDir = extractorService.extractZip(file);

            List<String> candidates = extractorService.findChangelogCandidates(extractDir);
            if (candidates.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new ChangelogInspectResponse(null, List.of(), null, List.of()));
            }

            // Auto-select the top-ranked candidate for property scanning
            String autoSelected = candidates.get(0);
            Path masterChangelog = extractorService.resolveChangelog(extractDir, autoSelected);

            List<String> unresolved = validatorService.findUnresolvedProperties(masterChangelog);
            String token = uploadSessionService.store(extractDir);

            log.info("Inspected '{}': {} candidate(s), auto-selected '{}', {} unresolved propert(ies)",
                    file.getOriginalFilename(), candidates.size(), autoSelected, unresolved.size());

            return ResponseEntity.ok(
                    new ChangelogInspectResponse(token, candidates, autoSelected, unresolved));

        } catch (Exception e) {
            log.error("Changelog inspection failed", e);
            return ResponseEntity.badRequest()
                    .body(new ChangelogInspectResponse(null, List.of(), null, List.of()));
        }
    }

    /**
     * Step 2 – Execute the changelog and capture the schema snapshot.
     * <p>Accepts either an {@code uploadToken} from a prior {@code /inspect} call (recommended,
     * no re-upload) or a direct {@code file} upload for one-shot use.
     * <p>{@code selectedChangelog} is the relative path within the extracted ZIP chosen by the
     * user from the dropdown.  Defaults to the top-ranked candidate when omitted.
     * <p>{@code properties} is a JSON object of user-supplied values for unresolved
     * {@code ${param}} placeholders, e.g. {@code {"service.schema.name":"public"}}.
     */
    @Operation(summary = "Execute a Liquibase changelog ZIP against embedded or external DB")
    @PostMapping("/execute")
    public ResponseEntity<LiquibaseExecutionResponse> execute(
            @RequestParam(value = "file",              required = false) MultipartFile file,
            @RequestParam(value = "uploadToken",       required = false) String uploadToken,
            @RequestParam(value = "selectedChangelog", required = false) String selectedChangelog,
            @RequestParam(value = "mode",              defaultValue = "embedded") String mode,
            @RequestParam(value = "snapshotName",      required = false) String snapshotName,
            @RequestParam(value = "externalUrl",       required = false) String externalUrl,
            @RequestParam(value = "externalUser",      required = false) String externalUser,
            @RequestParam(value = "externalPassword",  required = false) String externalPassword,
            @RequestParam(value = "properties",        required = false) String propertiesJson) {

        // Tracked so the finally block can always delete the extracted directory.
        Path extractDir = null;
        try {
            Path masterChangelog;
            String sourceName;

            if (uploadToken != null && !uploadToken.isBlank()) {
                extractDir = uploadSessionService.consume(uploadToken);
                if (selectedChangelog != null && !selectedChangelog.isBlank()) {
                    masterChangelog = extractorService.resolveChangelog(extractDir, selectedChangelog);
                } else {
                    // Fall back to auto-selection (same heuristic as inspect)
                    List<String> candidates = extractorService.findChangelogCandidates(extractDir);
                    if (candidates.isEmpty()) {
                        return ResponseEntity.badRequest().body(new LiquibaseExecutionResponse(
                                false, "No databaseChangeLog XML found in the uploaded ZIP.", null));
                    }
                    masterChangelog = extractorService.resolveChangelog(extractDir, candidates.get(0));
                }
                sourceName = masterChangelog.getFileName().toString();

            } else if (file != null && !file.isEmpty()) {
                extractDir = extractorService.extractZip(file);
                List<String> candidates = extractorService.findChangelogCandidates(extractDir);
                if (candidates.isEmpty()) {
                    return ResponseEntity.badRequest().body(new LiquibaseExecutionResponse(
                            false, "No databaseChangeLog XML found in the uploaded ZIP.", null));
                }
                String chosen = (selectedChangelog != null && !selectedChangelog.isBlank())
                        ? selectedChangelog : candidates.get(0);
                masterChangelog = extractorService.resolveChangelog(extractDir, chosen);
                sourceName = file.getOriginalFilename();

            } else {
                return ResponseEntity.badRequest().body(new LiquibaseExecutionResponse(
                        false, "Either 'file' or 'uploadToken' must be provided.", null));
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
            SchemaSnapshot snapshot;
            try {
                snapshot = introspectionService.introspect(connection, "public", name);
            } finally {
                connection.close();
            }

            SchemaSnapshotEntity saved = snapshotService.save(snapshot);

            return ResponseEntity.ok(new LiquibaseExecutionResponse(
                    true,
                    "Changelog executed successfully. " + snapshot.tables().size() + " tables found.",
                    saved.getId()));

        } catch (Exception e) {
            log.error("Liquibase execution failed", e);
            return ResponseEntity.badRequest().body(
                    new LiquibaseExecutionResponse(false, "Execution failed: " + e.getMessage(), null));
        } finally {
            // Always clean up the extracted directory — success or failure.
            extractorService.deleteDirectory(extractDir);
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
