package rajib.dev.utility.model;

public record ColumnInfo(
        String name,
        String dataType,
        String nativeType,
        int ordinalPosition,
        boolean nullable,
        String defaultValue,
        Integer columnSize,
        Integer decimalDigits,
        String comment,
        boolean primaryKey,
        boolean foreignKey
) {}
