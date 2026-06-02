package com.autoarchive.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
        List<Path> filePaths = collectFilePaths();
        return readMetadataParallel(filePaths);
    }

    private List<Path> collectFilePaths() {
        List<Path> filePaths = new ArrayList<>();
        for (String root : storageProperties.scanRoots()) {
            Path directory = Path.of(root);
            if (!Files.exists(directory)) {
                continue;
            }
            try (var paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile).forEach(filePaths::add);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to scan directory: " + directory, ex);
            }
        }
        return filePaths;
    }

    private List<FileMetadata> readMetadataParallel(List<Path> filePaths) {
        List<FileMetadata> metadataList = new ArrayList<>(filePaths.size());
        try (ExecutorService executor = virtualThreadArchiveExecutor.newExecutor()) {
            List<Future<FileMetadata>> futures = new ArrayList<>(filePaths.size());
            for (Path path : filePaths) {
                futures.add(executor.submit(() -> readMetadata(path)));
            }
            for (Future<FileMetadata> future : futures) {
                try {
                    metadataList.add(future.get());
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to read file metadata", ex);
                }
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
