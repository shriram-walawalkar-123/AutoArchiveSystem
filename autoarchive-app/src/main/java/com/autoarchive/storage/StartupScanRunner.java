package com.autoarchive.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.autoarchive.config.RetentionProperties;
import com.autoarchive.config.StorageProperties;

@Component
public class StartupScanRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupScanRunner.class);

    private final StorageProperties storageProperties;
    private final RetentionProperties retentionProperties;
    private final LocalFileStorageService localFileStorageService;
    private final RetentionPolicyEvaluator retentionPolicyEvaluator;

    public StartupScanRunner(
            StorageProperties storageProperties,
            RetentionProperties retentionProperties,
            LocalFileStorageService localFileStorageService,
            RetentionPolicyEvaluator retentionPolicyEvaluator) {
        this.storageProperties = storageProperties;
        this.retentionProperties = retentionProperties;
        this.localFileStorageService = localFileStorageService;
        this.retentionPolicyEvaluator = retentionPolicyEvaluator;
    }

    @Override
    public void run(ApplicationArguments args) {
        // log.info("Storage type: {}", storageProperties.type());
        // log.info("Scan roots: {}", storageProperties.scanRoots());
        // log.info("Archive root: {}", storageProperties.archiveRoot());
        log.info("Retention rule: archive files older than {} day(s)", retentionProperties.archiveAfterDays());

        var files = localFileStorageService.scanFilesInScanRoots();
        // log.info("Found {} file(s) under scan roots:", files.size());
        // for (FileMetadata file : files) {
        //     log.info(
        //             "  - {} | name={} | size={} bytes | lastModified={}",
        //             file.path().toAbsolutePath(),
        //             file.fileName(),
        //             file.sizeBytes(),
        //             file.lastModified());
        // }

        var candidates = retentionPolicyEvaluator.findArchiveCandidates(files);
        log.info("Would archive {} file(s) (dry run — no files moved):", candidates.size());
        for (FileMetadata file : candidates) {
            log.info("  -> {}", file.path().toAbsolutePath());
        }
    }
}