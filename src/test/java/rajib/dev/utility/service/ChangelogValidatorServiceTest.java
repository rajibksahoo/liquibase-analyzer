package rajib.dev.utility.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ChangelogValidatorServiceTest {

    private final ChangelogValidatorService service = new ChangelogValidatorService();

    @TempDir
    Path tempDir;

    // ── XML helpers ───────────────────────────────────────────────────────────

    private Path write(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
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

    private static String prop(String name, String value) {
        return "<property name=\"" + name + "\" value=\"" + value + "\"/>";
    }

    private static String include(String file) {
        return "<include file=\"" + file + "\"/>";
    }

    private static String changeSet(String sql) {
        return "<changeSet id=\"cs-" + sql.hashCode() + "\" author=\"test\">"
                + "<sql>" + sql + "</sql>"
                + "</changeSet>";
    }

    // ── findUnresolvedProperties ──────────────────────────────────────────────

    @Test
    void selfReferentialProperty_isReturned() throws Exception {
        Path f = write("master.xml", changelog(prop("schema.name", "${schema.name}")));

        assertThat(service.findUnresolvedProperties(f)).containsExactly("schema.name");
    }

    @Test
    void propertyReferencingUndefinedParam_isReturned() throws Exception {
        // 'a' references 'undefined' which is never declared → 'a' is unresolved
        Path f = write("master.xml", changelog(prop("a", "${undefined}")));

        assertThat(service.findUnresolvedProperties(f)).containsExactly("a");
    }

    @Test
    void propertyWithConcreteValue_isNotReturned() throws Exception {
        Path f = write("master.xml", changelog(prop("a", "concrete-value")));

        assertThat(service.findUnresolvedProperties(f)).isEmpty();
    }

    @Test
    void propertyReferencingAnotherDefinedProperty_isNotReturned() throws Exception {
        // b is concrete → a resolves transitively → neither is unresolved
        Path f = write("master.xml", changelog(prop("b", "real"), prop("a", "${b}")));

        assertThat(service.findUnresolvedProperties(f)).isEmpty();
    }

    @Test
    void multipleUnresolvedProperties_allReturned() throws Exception {
        Path f = write("master.xml", changelog(
                prop("x", "${x}"),
                prop("y", "${y}"),
                prop("z", "ok")));

        assertThat(service.findUnresolvedProperties(f))
                .containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void unresolvedPropertyInIncludedFile_isReturned() throws Exception {
        Path child  = write("child.xml", changelog(prop("child.prop", "${child.prop}")));
        Path master = write("master.xml", changelog(include(child.getFileName().toString())));

        assertThat(service.findUnresolvedProperties(master)).containsExactly("child.prop");
    }

    @Test
    void includedFileVisitedOnlyOnce_noDuplicates() throws Exception {
        // master includes child twice — property should still appear once
        Path child  = write("child.xml", changelog(prop("p", "${p}")));
        Path master = write("master.xml",
                changelog(include("child.xml"), include("child.xml")));

        assertThat(service.findUnresolvedProperties(master))
                .containsExactly("p");
    }

    // ── validatePropertyExpressions ───────────────────────────────────────────

    @Test
    void noCircularReference_doesNotThrow() throws Exception {
        Path f = write("master.xml", changelog(prop("a", "value"), prop("b", "${a}")));

        assertThatCode(() -> service.validatePropertyExpressions(f))
                .doesNotThrowAnyException();
    }

    @Test
    void circularMutualReference_throwsIllegalStateException() throws Exception {
        // a → ${b}, b → ${a}  ⟹ infinite expansion cycle
        Path f = write("master.xml", changelog(prop("a", "${b}"), prop("b", "${a}")));

        assertThatThrownBy(() -> service.validatePropertyExpressions(f))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circular property reference");
    }

    @Test
    void selfReferentialWithUserValue_doesNotThrow() throws Exception {
        Path f = write("master.xml", changelog(prop("schema.name", "${schema.name}")));

        assertThatCode(() ->
                service.validatePropertyExpressions(f, Map.of("schema.name", "public")))
                .doesNotThrowAnyException();
    }

    @Test
    void userValueBreaksMutualCycle() throws Exception {
        Path f = write("master.xml", changelog(prop("a", "${b}"), prop("b", "${a}")));

        // Providing 'a' as a concrete string cuts the a→b edge
        assertThatCode(() ->
                service.validatePropertyExpressions(f, Map.of("a", "resolved")))
                .doesNotThrowAnyException();
    }

    @Test
    void threeNodeCycle_detected() throws Exception {
        // a → b → c → a
        Path f = write("master.xml", changelog(
                prop("a", "${b}"),
                prop("b", "${c}"),
                prop("c", "${a}")));

        assertThatThrownBy(() -> service.validatePropertyExpressions(f))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circular property reference");
    }

    // ── findRequiredRoles ─────────────────────────────────────────────────────

    @Test
    void setRoleStatement_roleNameReturned() throws Exception {
        Path f = write("master.xml", changelog(changeSet("SET ROLE as_admin")));

        assertThat(service.findRequiredRoles(f)).containsExactly("as_admin");
    }

    @Test
    void setRole_caseInsensitive() throws Exception {
        Path f = write("master.xml", changelog(changeSet("set role AS_ADMIN")));

        // Role name is normalised to lowercase
        assertThat(service.findRequiredRoles(f)).containsExactly("as_admin");
    }

    @Test
    void noSetRole_returnsEmptySet() throws Exception {
        Path f = write("master.xml", changelog(prop("a", "b")));

        assertThat(service.findRequiredRoles(f)).isEmpty();
    }

    @Test
    void multipleSetRoles_allReturned() throws Exception {
        Path f = write("master.xml", changelog(
                changeSet("SET ROLE role_one"),
                changeSet("SET ROLE role_two")));

        assertThat(service.findRequiredRoles(f))
                .containsExactlyInAnyOrder("role_one", "role_two");
    }

    @Test
    void setRoleInIncludedFile_isFound() throws Exception {
        Path child  = write("child.xml",  changelog(changeSet("SET ROLE app_admin")));
        Path master = write("master.xml", changelog(include("child.xml")));

        assertThat(service.findRequiredRoles(master)).containsExactly("app_admin");
    }

    @Test
    void setRoleDuplicate_returnedOnlyOnce() throws Exception {
        // Two changesets both SET ROLE as_admin — should appear once in the result set
        Path f = write("master.xml", changelog(
                changeSet("SET ROLE as_admin"),
                changeSet("SET ROLE as_admin")));

        assertThat(service.findRequiredRoles(f)).containsExactly("as_admin");
    }
}
