package rajib.dev.utility.service;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the extracted changelog path between the /inspect and /execute requests
 * so the ZIP does not need to be uploaded twice.
 */
@Service
public class UploadSessionService {

    private final ConcurrentHashMap<String, Path> sessions = new ConcurrentHashMap<>();

    /** Stores the master-changelog path and returns a one-time token. */
    public String store(Path masterChangelogPath) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, masterChangelogPath);
        return token;
    }

    /**
     * Retrieves and removes the path for the given token.
     *
     * @throws IllegalArgumentException if the token is unknown or already consumed
     */
    public Path consume(String token) {
        Path path = sessions.remove(token);
        if (path == null) {
            throw new IllegalArgumentException(
                    "Upload session not found or already used: " + token);
        }
        return path;
    }
}
