package rajib.dev.utility.model;

import java.util.List;

public record PrimaryKeyInfo(
        String name,
        List<String> columns
) {}
