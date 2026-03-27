package rajib.dev.utility.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

class UploadSessionServiceTest {

    private UploadSessionService service;

    @BeforeEach
    void setUp() {
        service = new UploadSessionService();
    }

    @Test
    void store_returnsNonBlankUuidToken() {
        String token = service.store(Paths.get("/some/extract/dir"));

        assertThat(token).isNotBlank();
        // UUID format: 8-4-4-4-12 hex chars
        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void consume_returnsTheStoredPath() {
        Path path  = Paths.get("/extract/dir");
        String token = service.store(path);

        assertThat(service.consume(token)).isEqualTo(path);
    }

    @Test
    void consume_removesToken_subsequentConsumeFails() {
        String token = service.store(Paths.get("/dir"));
        service.consume(token);

        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(token);
    }

    @Test
    void consume_unknownToken_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.consume("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_calledTwice_returnsDistinctTokens() {
        String t1 = service.store(Paths.get("/dir/a"));
        String t2 = service.store(Paths.get("/dir/b"));

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void differentTokens_resolveIndependently() {
        Path pathA = Paths.get("/dir/a");
        Path pathB = Paths.get("/dir/b");

        String tokenA = service.store(pathA);
        String tokenB = service.store(pathB);

        assertThat(service.consume(tokenA)).isEqualTo(pathA);
        assertThat(service.consume(tokenB)).isEqualTo(pathB);
    }
}
