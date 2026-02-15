package rajib.dev.utility.dto;

import rajib.dev.utility.model.ColumnInfo;

import java.util.List;

public record ErNode(
        String id,
        String type,
        ErNodePosition position,
        ErNodeData data
) {
    public record ErNodePosition(double x, double y) {}

    public record ErNodeData(
            String tableName,
            String schema,
            String comment,
            List<ErColumnData> columns,
            String primaryKeyName
    ) {}

    public record ErColumnData(
            String name,
            String dataType,
            boolean nullable,
            boolean primaryKey,
            boolean foreignKey,
            String defaultValue
    ) {}
}
