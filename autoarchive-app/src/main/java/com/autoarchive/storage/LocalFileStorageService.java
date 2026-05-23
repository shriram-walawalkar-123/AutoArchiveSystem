package com.autoarchive.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public List<Path> listFilesInScanRoots() {
        List<Path> files = new ArrayList<>();
        for (String root : storageProperties.scanRoots()) {
            Path directory = Path.of(root);
            if (!Files.exists(directory)) {
                continue;
            }
            try (var paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile).forEach(files::add);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to scan directory: " + directory, ex);
            }
        }
        return files;
    }
}
