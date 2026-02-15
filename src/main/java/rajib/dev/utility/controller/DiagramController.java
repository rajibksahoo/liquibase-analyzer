package rajib.dev.utility.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rajib.dev.utility.dto.DiagramDataDto;
import rajib.dev.utility.dto.TableSubsetRequest;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.service.DiagramLayoutService;
import rajib.dev.utility.service.SchemaSnapshotService;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramLayoutService diagramLayoutService;
    private final SchemaSnapshotService snapshotService;

    public DiagramController(DiagramLayoutService diagramLayoutService,
                             SchemaSnapshotService snapshotService) {
        this.diagramLayoutService = diagramLayoutService;
        this.snapshotService = snapshotService;
    }

    @GetMapping("/{snapshotId}")
    public ResponseEntity<DiagramDataDto> getDiagram(@PathVariable Long snapshotId) {
        SchemaSnapshot snapshot = snapshotService.getSnapshot(snapshotId);
        DiagramDataDto diagram = diagramLayoutService.generateDiagram(snapshot, snapshotId);
        return ResponseEntity.ok(diagram);
    }

    @PostMapping("/subset")
    public ResponseEntity<DiagramDataDto> getSubsetDiagram(@RequestBody TableSubsetRequest request) {
        SchemaSnapshot snapshot = snapshotService.getSnapshot(request.snapshotId());
        DiagramDataDto diagram = diagramLayoutService.generateSubset(
                snapshot, request.snapshotId(),
                request.selectedTables(), request.includeIndirect());
        return ResponseEntity.ok(diagram);
    }
}
