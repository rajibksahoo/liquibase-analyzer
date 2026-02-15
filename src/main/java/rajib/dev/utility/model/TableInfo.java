package rajib.dev.utility.model;

import java.util.List;

public record TableInfo(
        String name,
        String schema,
        String comment,
        List<ColumnInfo> columns,
        PrimaryKeyInfo primaryKey,
        List<ForeignKeyInfo> foreignKeys,
        List<IndexInfo> indexes,
        List<UniqueConstraintInfo> uniqueConstraints,
        List<CheckConstraintInfo> checkConstraints
) {}
