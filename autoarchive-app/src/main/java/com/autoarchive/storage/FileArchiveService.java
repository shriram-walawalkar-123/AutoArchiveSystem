package com.autoarchive.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;

import com.autoarchive.config.StorageProperties;

@Service
public class FileArchiveService {

    private final StorageProperties storageProperties;

    public FileArchiveService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

  /**
   * Moves one file into the archive root (same filename).
   * Example: data/active/report.txt → data/archive/report.txt
   */
    public Path archiveFile(FileMetadata file) throws IOException {
        Path archiveRoot = Path.of(storageProperties.archiveRoot());
        Files.createDirectories(archiveRoot);

        Path target = archiveRoot.resolve(file.fileName());
        return Files.move(file.path(), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
