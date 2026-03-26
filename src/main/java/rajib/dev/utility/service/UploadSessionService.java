package rajib.dev.utility.service;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the extracted changelog directory between the /inspect and /execute
 * requests so the ZIP does not need to be uploaded twice.
 * The selected changelog file is resolved from this directory at execute time.
 */
@Service
public class UploadSessionService {

    private final ConcurrentHashMap<String, Path> sessions = new ConcurrentHashMap<>();

    /** Stores the extracted directory and returns a one-time token. */
    public String store(Path extractDir) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, extractDir);
        return token;
    }

    /**
     * Retrieves and removes the extract directory for the given token.
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
