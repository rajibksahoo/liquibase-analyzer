package rajib.dev.utility.model;

public record ForeignKeyInfo(
        String name,
        String columnName,
        String referencedTable,
        String referencedColumn,
        String updateRule,
        String deleteRule
) {}
