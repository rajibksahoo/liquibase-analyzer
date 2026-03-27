package rajib.dev.utility.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import rajib.dev.utility.entity.SchemaSnapshotEntity;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.service.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LiquibaseController.class)
class LiquibaseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ChangelogExtractorService extractorService;
    @MockBean ChangelogValidatorService validatorService;
    @MockBean UploadSessionService      uploadSessionService;
    @MockBean LiquibaseExecutionService executionService;
    @MockBean SchemaIntrospectionService introspectionService;
    @MockBean SchemaSnapshotService     snapshotService;
    @MockBean EmbeddedPgService         embeddedPgService;

    @TempDir
    Path tempDir;

    // ── POST /inspect ─────────────────────────────────────────────────────────

    @Test
    void inspect_success_returnsTokenCandidatesAndUnresolvedProperties() throws Exception {
        MockMultipartFile zip = new MockMultipartFile(
                "file", "changelog.zip", "application/zip", new byte[]{1, 2, 3});

        given(extractorService.extractZip(any())).willReturn(tempDir);
        given(extractorService.findChangelogCandidates(tempDir))
                .willReturn(List.of("db.changelog-master.xml", "changelogs/v1.xml"));
        given(extractorService.resolveChangelog(eq(tempDir), eq("db.changelog-master.xml")))
                .willReturn(tempDir.resolve("db.changelog-master.xml"));
        given(validatorService.findUnresolvedProperties(any()))
                .willReturn(List.of("service.schema.name", "service.tablespace.name"));
        given(uploadSessionService.store(tempDir)).willReturn("tok-xyz");

        mockMvc.perform(multipart("/api/liquibase/inspect").file(zip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadToken").value("tok-xyz"))
                .andExpect(jsonPath("$.selectedChangelog").value("db.changelog-master.xml"))
                .andExpect(jsonPath("$.changelogCandidates.length()").value(2))
                .andExpect(jsonPath("$.unresolvedProperties.length()").value(2))
                .andExpect(jsonPath("$.unresolvedProperties[0]").value("service.schema.name"));
    }

    @Test
    void inspect_noChangelogFound_returnsBadRequest() throws Exception {
        MockMultipartFile zip = new MockMultipartFile(
                "file", "empty.zip", "application/zip", new byte[]{});

        given(extractorService.extractZip(any())).willReturn(tempDir);
        given(extractorService.findChangelogCandidates(tempDir)).willReturn(List.of());

        mockMvc.perform(multipart("/api/liquibase/inspect").file(zip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inspect_extractionThrows_returnsBadRequest() throws Exception {
        MockMultipartFile zip = new MockMultipartFile(
                "file", "bad.zip", "application/zip", new byte[]{0});

        given(extractorService.extractZip(any()))
                .willThrow(new RuntimeException("Zip extraction failed"));

        mockMvc.perform(multipart("/api/liquibase/inspect").file(zip))
                .andExpect(status().isBadRequest());
    }

    // ── POST /execute ─────────────────────────────────────────────────────────

    @Test
    void execute_withUploadToken_returnsSuccessAndSnapshotId() throws Exception {
        Connection conn = mock(Connection.class);
        SchemaSnapshot snapshot = new SchemaSnapshot("snap", "public", Instant.now(), List.of());
        SchemaSnapshotEntity entity =
                new SchemaSnapshotEntity("snap", "public", Instant.now(), 0, "{}");
        entity.setId(99L);

        given(uploadSessionService.consume("tok-abc")).willReturn(tempDir);
        given(extractorService.resolveChangelog(eq(tempDir), eq("master.xml")))
                .willReturn(tempDir.resolve("master.xml"));
        given(executionService.executeChangelog(any(), any(), any(), any(), any(), any()))
                .willReturn(conn);
        given(introspectionService.introspect(any(), any(), any())).willReturn(snapshot);
        given(snapshotService.save(any())).willReturn(entity);

        mockMvc.perform(multipart("/api/liquibase/execute")
                        .param("uploadToken",       "tok-abc")
                        .param("selectedChangelog", "master.xml")
                        .param("mode",              "embedded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.snapshotId").value(99));
    }

    @Test
    void execute_withUserSuppliedProperties_passedThroughToService() throws Exception {
        Connection conn = mock(Connection.class);
        SchemaSnapshot snapshot = new SchemaSnapshot("s", "public", Instant.now(), List.of());
        SchemaSnapshotEntity entity =
                new SchemaSnapshotEntity("s", "public", Instant.now(), 0, "{}");
        entity.setId(1L);

        given(uploadSessionService.consume("tok-props")).willReturn(tempDir);
        given(extractorService.resolveChangelog(any(), any()))
                .willReturn(tempDir.resolve("master.xml"));
        given(executionService.executeChangelog(any(), any(), any(), any(), any(), any()))
                .willReturn(conn);
        given(introspectionService.introspect(any(), any(), any())).willReturn(snapshot);
        given(snapshotService.save(any())).willReturn(entity);

        String propertiesJson = objectMapper.writeValueAsString(
                java.util.Map.of("service.schema.name", "public",
                                 "service.tablespace.name", "pg_default"));

        mockMvc.perform(multipart("/api/liquibase/execute")
                        .param("uploadToken",       "tok-props")
                        .param("selectedChangelog", "master.xml")
                        .param("mode",              "embedded")
                        .param("properties",        propertiesJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify service was called with the parsed property map
        then(executionService).should().executeChangelog(
                any(), any(), any(), any(), any(),
                argThat(m -> "public".equals(m.get("service.schema.name"))));
    }

    @Test
    void execute_neitherFileNorToken_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/liquibase/execute").param("mode", "embedded"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void execute_unknownToken_returnsBadRequest() throws Exception {
        given(uploadSessionService.consume("bad-token"))
                .willThrow(new IllegalArgumentException("Upload session not found: bad-token"));

        mockMvc.perform(multipart("/api/liquibase/execute")
                        .param("uploadToken", "bad-token")
                        .param("mode",        "embedded"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not found")));
    }

    @Test
    void execute_executionServiceThrows_returnsBadRequest() throws Exception {
        given(uploadSessionService.consume("tok-fail")).willReturn(tempDir);
        given(extractorService.resolveChangelog(any(), any()))
                .willReturn(tempDir.resolve("master.xml"));
        given(executionService.executeChangelog(any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("Liquibase error"));

        mockMvc.perform(multipart("/api/liquibase/execute")
                        .param("uploadToken",       "tok-fail")
                        .param("selectedChangelog", "master.xml")
                        .param("mode",              "embedded"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /status ───────────────────────────────────────────────────────────

    @Test
    void status_embeddedPgRunning_returnsStatusFields() throws Exception {
        given(embeddedPgService.isRunning()).willReturn(true);
        given(embeddedPgService.getPort()).willReturn(54321);

        mockMvc.perform(get("/api/liquibase/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddedPgRunning").value(true))
                .andExpect(jsonPath("$.embeddedPgPort").value(54321));
    }

    @Test
    void status_embeddedPgStopped_returnsFalse() throws Exception {
        given(embeddedPgService.isRunning()).willReturn(false);
        given(embeddedPgService.getPort()).willReturn(-1);

        mockMvc.perform(get("/api/liquibase/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddedPgRunning").value(false));
    }
}
