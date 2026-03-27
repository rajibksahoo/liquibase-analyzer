package rajib.dev.utility.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the extracted changelog directory between the /inspect and /execute
 * requests so the ZIP does not need to be uploaded twice.
 *
 * <p>Sessions are one-time-use: {@link #consume} removes the token so it cannot
 * be replayed. A background task evicts sessions that have not been consumed
 * within {@value #SESSION_TTL_MINUTES} minutes and deletes their directories.
 */
@Service
public class UploadSessionService {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionService.class);

    /** Sessions older than this are evicted by the background cleanup task. */
    static final int SESSION_TTL_MINUTES = 30;

    private record SessionEntry(Path extractDir, Instant createdAt) {}

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    private final ChangelogExtractorService extractorService;

    public UploadSessionService(ChangelogExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    /** Stores the extracted directory and returns a one-time token. */
    public String store(Path extractDir) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionEntry(extractDir, Instant.now()));
        return token;
    }

    /**
     * Retrieves and removes the extract directory for the given token.
     * The caller is responsible for deleting the directory when done.
     *
     * @throws IllegalArgumentException if the token is unknown or already consumed
     */
    public Path consume(String token) {
        SessionEntry entry = sessions.remove(token);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Upload session not found or already used: " + token);
        }
        return entry.extractDir();
    }

    /**
     * Evicts sessions older than {@value #SESSION_TTL_MINUTES} minutes and deletes
     * their extracted directories. Runs every 5 minutes.
     */
    @Scheduled(fixedRateString = "PT5M")
    public void evictExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL_MINUTES, ChronoUnit.MINUTES);
        int evicted = 0;
        Iterator<Map.Entry<String, SessionEntry>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SessionEntry> entry = it.next();
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                it.remove();
                extractorService.deleteDirectory(entry.getValue().extractDir());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("Evicted {} expired upload session(s) (TTL {} min)", evicted, SESSION_TTL_MINUTES);
        }
    }
}
