package rajib.dev.utility.dto;

import java.time.Instant;

public record SchemaSnapshotSummaryDto(
        Long id,
        String name,
        String schemaName,
        Instant capturedAt,
        int tableCount
) {}
