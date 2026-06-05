package com.autoarchive.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.autoarchive.config.StorageProperties;

@Service
public class LocalFileStorageService {

    private final StorageProperties storageProperties;
    private final VirtualThreadArchiveExecutor virtualThreadArchiveExecutor;

    public LocalFileStorageService(
            StorageProperties storageProperties,
            VirtualThreadArchiveExecutor virtualThreadArchiveExecutor) {
        this.storageProperties = storageProperties;
        this.virtualThreadArchiveExecutor = virtualThreadArchiveExecutor;
    }

    public List<FileMetadata> scanFilesInScanRoots() {
        try (ExecutorService executor = virtualThreadArchiveExecutor.newExecutor()) {
            List<java.util.concurrent.Future<FileMetadata>> futures = new ArrayList<>();
            for (String root : storageProperties.scanRoots()) {
                Path directory = Path.of(root);
                if (!Files.exists(directory)) {
                    continue;
                }
                try (var paths = Files.walk(directory)) {
                    paths.filter(Files::isRegularFile)
                            .forEach(path -> futures.add(executor.submit(() -> readMetadata(path))));
                } catch (IOException ex) {
                    throw new UncheckedIOException("Failed to scan directory: " + directory, ex);
                }
            }
            List<FileMetadata> results = new ArrayList<>(futures.size());
            for (var future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to read file metadata", ex);
                }
            }
            return results;
        }
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
