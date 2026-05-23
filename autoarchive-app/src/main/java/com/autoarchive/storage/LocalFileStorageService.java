package com.autoarchive.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.autoarchive.config.StorageProperties;

@Service
public class LocalFileStorageService {

    private final StorageProperties storageProperties;

    public LocalFileStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public List<FileMetadata> scanFilesInScanRoots() {
        List<FileMetadata> metadataList = new ArrayList<>();
        for (String root : storageProperties.scanRoots()) {
            Path directory = Path.of(root);
            if (!Files.exists(directory)) {
                continue;
            }
            try (var paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> metadataList.add(readMetadata(path)));
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to scan directory: " + directory, ex);
            }
        }
        return metadataList;
    }

    private FileMetadata readMetadata(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return new FileMetadata(
                    path,
                    path.getFileName().toString(),
                    attrs.size(),
                    attrs.lastModifiedTime().toInstant());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read metadata: " + path, ex);
        }
    }
}