package rajib.dev.utility.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the changelog-discovery methods in {@link ChangelogExtractorService}.
 * ZIP extraction is covered by the integration test.
 */
class ChangelogExtractorCandidatesTest {

    // Instantiate directly — no Spring context needed; @Value field is not exercised here.
    private final ChangelogExtractorService service = new ChangelogExtractorService();

    @TempDir
    Path extractDir;

    // ── XML helpers ───────────────────────────────────────────────────────────

    private Path write(String relativePath, String content) throws Exception {
        Path file = extractDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static String changelog(String... body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<databaseChangeLog xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\">"
                + String.join("", body)
                + "</databaseChangeLog>";
    }

    private static String nonChangelog() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><configuration/>";
    }

    // ── findChangelogCandidates ───────────────────────────────────────────────

    @Test
    void singleChangelogFile_returned() throws Exception {
        write("db.changelog-master.xml", changelog());

        assertThat(service.findChangelogCandidates(extractDir))
                .containsExactly("db.changelog-master.xml");
    }

    @Test
    void conventionalMasterName_rankedFirst() throws Exception {
        write("other.xml",               changelog());
        write("db.changelog-master.xml", changelog());

        assertThat(service.findChangelogCandidates(extractDir).get(0))
                .isEqualTo("db.changelog-master.xml");
    }

    @Test
    void fileWithIncludes_rankedBeforeFileWithout() throws Exception {
        write("plain.xml",  changelog());
        write("master.xml", changelog("<include file=\"plain.xml\"/>"));

        assertThat(service.findChangelogCandidates(extractDir).get(0))
                .isEqualTo("master.xml");
    }

    @Test
    void shallowerFile_rankedBeforeDeeperFile() throws Exception {
        write("sub/deep.xml", changelog());
        write("root.xml",     changelog());

        assertThat(service.findChangelogCandidates(extractDir).get(0))
                .isEqualTo("root.xml");
    }

    @Test
    void alphabeticalOrderWithinSameDepth() throws Exception {
        write("z-changelog.xml", changelog());
        write("a-changelog.xml", changelog());

        List<String> result = service.findChangelogCandidates(extractDir);
        assertThat(result).containsExactly("a-changelog.xml", "z-changelog.xml");
    }

    @Test
    void nonChangelogXml_excluded() throws Exception {
        write("db.changelog-master.xml", changelog());
        write("config.xml",              nonChangelog());

        assertThat(service.findChangelogCandidates(extractDir))
                .containsExactly("db.changelog-master.xml");
    }

    @Test
    void invalidXml_excluded() throws Exception {
        write("db.changelog-master.xml", changelog());
        write("broken.xml",              "this is not xml <<<");

        assertThat(service.findChangelogCandidates(extractDir))
                .containsExactly("db.changelog-master.xml");
    }

    @Test
    void noCandidates_returnsEmptyList() throws Exception {
        write("config.xml", nonChangelog());

        assertThat(service.findChangelogCandidates(extractDir)).isEmpty();
    }

    @Test
    void resultPaths_useForwardSlashSeparator() throws Exception {
        write("sub/dir/changelog.xml", changelog());

        assertThat(service.findChangelogCandidates(extractDir))
                .allMatch(p -> !p.contains("\\"));
    }

    @Test
    void multipleCandidates_allReturned() throws Exception {
        write("db.changelog-master.xml", changelog());
        write("changelogs/v1.xml",        changelog());
        write("changelogs/v2.xml",        changelog());

        assertThat(service.findChangelogCandidates(extractDir)).hasSize(3);
    }

    // ── resolveChangelog ──────────────────────────────────────────────────────

    @Test
    void resolveChangelog_rootFile_returnsAbsolutePath() throws Exception {
        write("master.xml", changelog());

        Path resolved = service.resolveChangelog(extractDir, "master.xml");

        assertThat(resolved).isAbsolute();
        assertThat(resolved.getFileName().toString()).isEqualTo("master.xml");
    }

    @Test
    void resolveChangelog_nestedRelativePath() throws Exception {
        write("sub/child.xml", changelog());

        Path resolved = service.resolveChangelog(extractDir, "sub/child.xml");

        assertThat(resolved.getFileName().toString()).isEqualTo("child.xml");
        assertThat(resolved.getParent().getFileName().toString()).isEqualTo("sub");
    }
}
