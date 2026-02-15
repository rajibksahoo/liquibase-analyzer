package rajib.dev.utility.model;

import java.util.List;

public record UniqueConstraintInfo(
        String name,
        List<String> columns
) {}
