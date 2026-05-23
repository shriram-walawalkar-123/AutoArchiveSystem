package com.autoarchive.storage;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Metadata for one file on the local filesystem (used before DB storage exists).
 */
public record FileMetadata(
        Path path,
        String fileName,
        long sizeBytes,
        Instant lastModified) {
}
