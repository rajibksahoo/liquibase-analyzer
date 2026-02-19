package rajib.dev.utility.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rajib.dev.utility.dto.SchemaSnapshotDetailDto;
import rajib.dev.utility.dto.SchemaSnapshotSummaryDto;
import rajib.dev.utility.entity.SchemaSnapshotEntity;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.service.EmbeddedPgService;
import rajib.dev.utility.service.SchemaIntrospectionService;
import rajib.dev.utility.service.SchemaSnapshotService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schemas")
@Tag(name = "Schema Snapshots")
public class SchemaController {

    private final SchemaSnapshotService snapshotService;
    private final SchemaIntrospectionService introspectionService;
    private final EmbeddedPgService embeddedPgService;

    public SchemaController(SchemaSnapshotService snapshotService,
                            SchemaIntrospectionService introspectionService,
                            EmbeddedPgService embeddedPgService) {
        this.snapshotService = snapshotService;
        this.introspectionService = introspectionService;
        this.embeddedPgService = embeddedPgService;
    }

    @Operation(summary = "List all schema snapshots")
    @GetMapping
    public ResponseEntity<List<SchemaSnapshotSummaryDto>> listSnapshots() {
        return ResponseEntity.ok(snapshotService.listAll());
    }

    @Operation(summary = "Get snapshot details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<SchemaSnapshotDetailDto> getSnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(snapshotService.getDetail(id));
    }

    @Operation(summary = "Delete a snapshot by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSnapshot(@PathVariable Long id) {
        snapshotService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Introspect a live database and create a snapshot")
    @PostMapping("/introspect")
    public ResponseEntity<?> introspect(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");
            String user = request.get("user");
            String password = request.get("password");
            String schema = request.getOrDefault("schema", "public");
            String name = request.getOrDefault("name", "Introspection snapshot");

            Connection connection;
            if (url != null && !url.isBlank()) {
                connection = DriverManager.getConnection(url, user, password);
            } else if (embeddedPgService.isRunning()) {
                connection = embeddedPgService.getConnection();
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "No database connection available. Provide URL or start embedded PG first."));
            }

            SchemaSnapshot snapshot = introspectionService.introspect(connection, schema, name);
            connection.close();
            SchemaSnapshotEntity saved = snapshotService.save(snapshot);

            return ResponseEntity.ok(Map.of(
                    "snapshotId", saved.getId(),
                    "tableCount", snapshot.tables().size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
