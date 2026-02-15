package rajib.dev.utility.dto;

import java.util.List;

public record TableSubsetRequest(
        Long snapshotId,
        List<String> selectedTables,
        boolean includeIndirect
) {}
