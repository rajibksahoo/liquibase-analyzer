package rajib.dev.utility.dto;

public record ErEdge(
        String id,
        String source,
        String target,
        String sourceHandle,
        String targetHandle,
        String type,
        ErEdgeData data
) {
    public record ErEdgeData(
            String constraintName,
            String sourceColumn,
            String targetColumn,
            String deleteRule,
            String updateRule
    ) {}
}
