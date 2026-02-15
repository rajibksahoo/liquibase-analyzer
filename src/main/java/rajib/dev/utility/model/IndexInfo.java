package rajib.dev.utility.model;

import java.util.List;

public record IndexInfo(
        String name,
        List<String> columns,
        boolean unique,
        String type
) {}
