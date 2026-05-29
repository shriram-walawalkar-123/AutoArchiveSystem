package com.autoarchive.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.autoarchive.config.ArchiveExecutionProperties;
import com.autoarchive.config.RetentionProperties;
import com.autoarchive.config.StorageProperties;

@Component
public class StartupScanRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupScanRunner.class);

    private final StorageProperties storageProperties;
    private final RetentionProperties retentionProperties;
    private final ArchiveExecutionProperties archiveExecutionProperties;
    private final LocalFileStorageService localFileStorageService;
    private final RetentionPolicyEvaluator retentionPolicyEvaluator;
    private final ArchivePipelineService archivePipelineService;

    public StartupScanRunner(
            StorageProperties storageProperties,
            RetentionProperties retentionProperties,
            ArchiveExecutionProperties archiveExecutionProperties,
            LocalFileStorageService localFileStorageService,
            RetentionPolicyEvaluator retentionPolicyEvaluator,
            ArchivePipelineService archivePipelineService) {
        this.storageProperties = storageProperties;
        this.retentionProperties = retentionProperties;
        this.archiveExecutionProperties = archiveExecutionProperties;
        this.localFileStorageService = localFileStorageService;
        this.retentionPolicyEvaluator = retentionPolicyEvaluator;
        this.archivePipelineService = archivePipelineService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Archive root: {}", storageProperties.archiveRoot());
        log.info(
                "Retention rule: archive files older than {} day(s) | dry-run={} | execution-mode={}",
                retentionProperties.archiveAfterDays(),
                retentionProperties.dryRun(),
                archiveExecutionProperties.mode());

        var files = localFileStorageService.scanFilesInScanRoots();
        var candidates = retentionPolicyEvaluator.findArchiveCandidates(files);

        if (retentionProperties.dryRun()) {
            log.info("Dry run — would archive {} file(s):", candidates.size());
            for (FileMetadata file : candidates) {
                log.info("  -> {}", file.path().toAbsolutePath());
            }
            return;
        }

        ArchiveExecutionResult result = archivePipelineService.execute(
                candidates, archiveExecutionProperties.mode());

        log.info(
                "Archive finished | mode={} | files={} | success={} | failed={} | time={} ms",
                result.mode(),
                result.fileCount(),
                result.successCount(),
                result.failureCount(),
                result.durationMillis());
    }
}
