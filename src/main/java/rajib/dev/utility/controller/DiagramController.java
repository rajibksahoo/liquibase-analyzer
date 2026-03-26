package rajib.dev.utility.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rajib.dev.utility.dto.DiagramDataDto;
import rajib.dev.utility.dto.TableSubsetRequest;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.service.DiagramLayoutService;
import rajib.dev.utility.service.SchemaSnapshotService;

@RestController
@RequestMapping("/api/diagrams")
@Tag(name = "ER Diagrams")
public class DiagramController {

    private final DiagramLayoutService diagramLayoutService;
    private final SchemaSnapshotService snapshotService;

    public DiagramController(DiagramLayoutService diagramLayoutService,
                             SchemaSnapshotService snapshotService) {
        this.diagramLayoutService = diagramLayoutService;
        this.snapshotService = snapshotService;
    }

    @Operation(summary = "Get full ER diagram for a snapshot")
    @GetMapping("/{snapshotId}")
    public ResponseEntity<DiagramDataDto> getDiagram(@PathVariable Long snapshotId) {
        SchemaSnapshot snapshot = snapshotService.getSnapshot(snapshotId);
        DiagramDataDto diagram = diagramLayoutService.generateDiagram(snapshot, snapshotId);
        return ResponseEntity.ok(diagram);
    }

    @Operation(summary = "Get filtered ER diagram for selected tables")
    @PostMapping("/subset")
    public ResponseEntity<DiagramDataDto> getSubsetDiagram(@RequestBody TableSubsetRequest request) {
        SchemaSnapshot snapshot = snapshotService.getSnapshot(request.snapshotId());
        DiagramDataDto diagram = diagramLayoutService.generateSubset(
                snapshot, request.snapshotId(),
                request.selectedTables(), request.includeIndirect());
        return ResponseEntity.ok(diagram);
    }
}
