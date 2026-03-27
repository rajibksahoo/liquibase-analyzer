package rajib.dev.utility.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ChangelogExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogExtractorService.class);

    @Value("${app.upload-dir:./data/uploads}")
    private String uploadDir;

    // -------------------------------------------------------------------------
    // ZIP extraction
    // -------------------------------------------------------------------------

    public Path extractZip(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        String baseName = file.getOriginalFilename();
        if (baseName != null && baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        }

        Path extractDir = uploadPath.resolve(baseName + "_" + System.currentTimeMillis())
                .toAbsolutePath().normalize();
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

    // -------------------------------------------------------------------------
    // Changelog discovery
    // -------------------------------------------------------------------------

    /**
     * Scans the extracted directory for all XML files whose root element is
     * {@code <databaseChangeLog>} and returns their paths sorted by likeliness
     * of being the master changelog:
     * <ol>
     *   <li>Files named {@code db.changelog-master.xml} (conventional name)</li>
     *   <li>Files that contain {@code <include>} or {@code <includeAll>} (aggregators)</li>
     *   <li>Shallower paths before deeper ones</li>
     *   <li>Alphabetical within the same depth</li>
     * </ol>
     *
     * @return relative path strings (forward-slash separated) for use in the UI
     */
    public List<String> findChangelogCandidates(Path extractDir) throws IOException {
        List<Path> xmlFiles = new ArrayList<>();
        try (var stream = Files.walk(extractDir)) {
            stream.filter(p -> p.toString().endsWith(".xml"))
                  .filter(p -> isChangelogXml(p))
                  .forEach(xmlFiles::add);
        }

        xmlFiles.sort(Comparator
                .comparingInt((Path p) -> isMasterName(p)    ? 0 : 1)
                .thenComparingInt(p    -> hasIncludes(p)     ? 0 : 1)
                .thenComparingInt(p    -> extractDir.relativize(p).getNameCount())
                .thenComparing(p       -> p.getFileName().toString()));

        List<String> relativePaths = new ArrayList<>();
        for (Path p : xmlFiles) {
            // Always forward-slash so the frontend and controller agree on the separator
            relativePaths.add(extractDir.relativize(p).toString().replace('\\', '/'));
        }

        log.info("Found {} changelog candidate(s) in {}", relativePaths.size(), extractDir.getFileName());
        return relativePaths;
    }

    /**
     * Resolves a relative changelog path (as returned by {@link #findChangelogCandidates})
     * back to an absolute {@link Path}.
     */
    public Path resolveChangelog(Path extractDir, String relativePath) {
        return extractDir.resolve(relativePath).toAbsolutePath().normalize();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Returns true if the XML file's root element is databaseChangeLog. */
    private boolean isChangelogXml(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            Document doc = builder.parse(path.toFile());
            String root = doc.getDocumentElement().getLocalName();
            if (root == null) {
                root = doc.getDocumentElement().getNodeName();
            }
            return root != null && root.endsWith("databaseChangeLog");
        } catch (Exception e) {
            return false; // not a valid/relevant XML
        }
    }

    private boolean isMasterName(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.equals("db.changelog-master.xml") || name.equals("changelog-master.xml");
    }

    /** Returns true if the file has <include> or <includeAll> elements (master aggregator). */
    private boolean hasIncludes(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("<include ") || content.contains("<includeAll");
        } catch (Exception e) {
            return false;
        }
    }
}
