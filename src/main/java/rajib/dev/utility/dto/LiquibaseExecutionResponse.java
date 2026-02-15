package rajib.dev.utility.dto;

public record LiquibaseExecutionResponse(
        boolean success,
        String message,
        Long snapshotId
) {}
