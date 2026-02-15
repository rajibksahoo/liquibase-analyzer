package rajib.dev.utility.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ChangelogExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogExtractorService.class);
    private static final String MASTER_CHANGELOG = "db.changelog-master.xml";

    @Value("${app.upload-dir:./data/uploads}")
    private String uploadDir;

    public Path extractZip(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        String baseName = file.getOriginalFilename();
        if (baseName != null && baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        }

        Path extractDir = uploadPath.resolve(baseName + "_" + System.currentTimeMillis()).toAbsolutePath().normalize();
        Files.createDirectories(extractDir);

        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                // Prevent zip-slip
                Path entryPath = extractDir.resolve(entryName).toAbsolutePath().normalize();
                if (!entryPath.startsWith(extractDir)) {
                    throw new IOException("Zip entry outside target directory: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        log.info("Extracted ZIP to {}", extractDir);
        return extractDir;
    }

    public Path findMasterChangelog(Path extractDir) throws IOException {
        // Search for the master changelog file
        try (var stream = Files.walk(extractDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().equals(MASTER_CHANGELOG))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "Master changelog not found. Expected: " + MASTER_CHANGELOG));
        }
    }
}
