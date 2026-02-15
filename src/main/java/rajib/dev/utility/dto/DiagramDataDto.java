package rajib.dev.utility.dto;

import java.util.List;

public record DiagramDataDto(
        Long snapshotId,
        String snapshotName,
        List<ErNode> nodes,
        List<ErEdge> edges
) {}
