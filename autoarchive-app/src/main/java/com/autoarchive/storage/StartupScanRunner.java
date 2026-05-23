package com.autoarchive.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.autoarchive.config.StorageProperties;

@Component
public class StartupScanRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupScanRunner.class);

    private final StorageProperties storageProperties;
    private final LocalFileStorageService localFileStorageService;

    public StartupScanRunner(
            StorageProperties storageProperties, LocalFileStorageService localFileStorageService) {
        this.storageProperties = storageProperties;
        this.localFileStorageService = localFileStorageService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        log.info("Storage type: {}", storageProperties.type());
        log.info("Scan roots: {}", storageProperties.scanRoots());
        log.info("Archive root: {}", storageProperties.archiveRoot());

        var files = localFileStorageService.listFilesInScanRoots();
        log.info("Found {} file(s) under scan roots:", files.size());
        for (Path file : files) {
            long sizeBytes = Files.size(file);
            log.info("  - {} ({} bytes)", file.toAbsolutePath(), sizeBytes);
        }
    }
}
