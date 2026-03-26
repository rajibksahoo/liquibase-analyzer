package rajib.dev.utility.dto;

import java.util.List;

public record ChangelogInspectResponse(
        String uploadToken,
        /** All databaseChangeLog XML files found in the ZIP, sorted by master-likelihood. */
        List<String> changelogCandidates,
        /** The pre-selected best candidate (first in the sorted list). */
        String selectedChangelog,
        /** Property names whose values are self-referential or undefined — require user input. */
        List<String> unresolvedProperties
) {}
