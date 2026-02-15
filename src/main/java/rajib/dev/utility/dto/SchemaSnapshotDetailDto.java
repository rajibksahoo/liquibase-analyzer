package rajib.dev.utility.dto;

import rajib.dev.utility.model.TableInfo;

import java.time.Instant;
import java.util.List;

public record SchemaSnapshotDetailDto(
        Long id,
        String name,
        String schemaName,
        Instant capturedAt,
        List<TableInfo> tables
) {}
