package rajib.dev.utility.model;

import java.time.Instant;
import java.util.List;

public record SchemaSnapshot(
        String name,
        String schemaName,
        Instant capturedAt,
        List<TableInfo> tables
) {}
