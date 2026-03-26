package rajib.dev.utility.dto;

import java.util.List;

public record ChangelogInspectResponse(
        String uploadToken,
        List<String> unresolvedProperties
) {}
