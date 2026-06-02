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

    /** Moves file to flat archive root: data/archive/{fileName} */
    public Path archiveFile(FileMetadata file) throws IOException {
        Path archiveRoot = Path.of(storageProperties.archiveRoot());
        Files.createDirectories(archiveRoot);
        Path target = archiveRoot.resolve(file.fileName());
        return Files.move(file.path(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Moves file into a shard subfolder to avoid Windows directory lock contention
     * when many threads write to the same folder: data/archive/shard-{n}/{fileName}
     */
    public Path archiveFileSharded(FileMetadata file, int fileIndex, int shardCount) throws IOException {
        Path archiveRoot = Path.of(storageProperties.archiveRoot());
        int shard = Math.floorMod(fileIndex, shardCount);
        Path shardDir = archiveRoot.resolve("shard-" + shard);
        Files.createDirectories(shardDir);
        Path target = shardDir.resolve(file.fileName());
        return Files.move(file.path(), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
