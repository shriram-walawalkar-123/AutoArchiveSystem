package com.autoarchive.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.autoarchive.audit.AuditService;
import com.autoarchive.config.RetentionProperties;
import com.autoarchive.config.StorageProperties;

@Component
public class StartupScanRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupScanRunner.class);

    private final StorageProperties storageProperties;
    private final RetentionProperties retentionProperties;
    private final LocalFileStorageService localFileStorageService;
    private final RetentionPolicyEvaluator retentionPolicyEvaluator;
    private final FileArchiveService fileArchiveService;
    private final AuditService auditService;

    public StartupScanRunner(
            StorageProperties storageProperties,
            RetentionProperties retentionProperties,
            LocalFileStorageService localFileStorageService,
            RetentionPolicyEvaluator retentionPolicyEvaluator,
            FileArchiveService fileArchiveService,
            AuditService auditService) {
        this.storageProperties = storageProperties;
        this.retentionProperties = retentionProperties;
        this.localFileStorageService = localFileStorageService;
        this.retentionPolicyEvaluator = retentionPolicyEvaluator;
        this.fileArchiveService = fileArchiveService;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Archive root: {}", storageProperties.archiveRoot());
        log.info(
                "Retention rule: archive files older than {} day(s) | dry-run={}",
                retentionProperties.archiveAfterDays(),
                retentionProperties.dryRun());

        var files = localFileStorageService.scanFilesInScanRoots();
        var candidates = retentionPolicyEvaluator.findArchiveCandidates(files);

        if (retentionProperties.dryRun()) {
            log.info("Dry run — would archive {} file(s):", candidates.size());
            for (FileMetadata file : candidates) {
                log.info("  -> {}", file.path().toAbsolutePath());
            }
            return;
        }

        for (FileMetadata file : candidates) {
            try {
                var target = fileArchiveService.archiveFile(file);
                log.info("  -> moved {} to {}", file.path().toAbsolutePath(), target.toAbsolutePath());
                auditService.logArchiveSuccess(file, target);
            } catch (Exception ex) {
                log.error("  -> failed to archive {}: {}", file.path().toAbsolutePath(), ex.getMessage());
                auditService.logArchiveFailure(file, ex.getMessage());
            }
        }
    }
}
