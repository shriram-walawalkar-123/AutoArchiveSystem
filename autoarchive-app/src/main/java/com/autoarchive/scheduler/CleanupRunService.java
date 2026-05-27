package com.autoarchive.scheduler;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.autoarchive.audit.AuditService;
import com.autoarchive.config.RetentionProperties;
import com.autoarchive.config.StorageProperties;
import com.autoarchive.storage.FileArchiveService;
import com.autoarchive.storage.FileMetadata;
import com.autoarchive.storage.LocalFileStorageService;
import com.autoarchive.storage.RetentionPolicyEvaluator;

@Service
public class CleanupRunService {

    private static final Logger log = LoggerFactory.getLogger(CleanupRunService.class);

    private final StorageProperties storageProperties;
    private final RetentionProperties retentionProperties;
    private final LocalFileStorageService localFileStorageService;
    private final RetentionPolicyEvaluator retentionPolicyEvaluator;
    private final FileArchiveService fileArchiveService;
    private final AuditService auditService;
    private final CleanupExecutorService cleanupExecutorService;

    public CleanupRunService(
            StorageProperties storageProperties,
            RetentionProperties retentionProperties,
            LocalFileStorageService localFileStorageService,
            RetentionPolicyEvaluator retentionPolicyEvaluator,
            FileArchiveService fileArchiveService,
            AuditService auditService,
            CleanupExecutorService cleanupExecutorService) {
        this.storageProperties = storageProperties;
        this.retentionProperties = retentionProperties;
        this.localFileStorageService = localFileStorageService;
        this.retentionPolicyEvaluator = retentionPolicyEvaluator;
        this.fileArchiveService = fileArchiveService;
        this.auditService = auditService;
        this.cleanupExecutorService = cleanupExecutorService;
    }

    public void runOnce(String contextName) {
        log.info("[{}] Archive root: {}", contextName, storageProperties.archiveRoot());

        var files = localFileStorageService.scanFilesInScanRoots();
        var candidates = retentionPolicyEvaluator.findArchiveCandidates(files);

        if (retentionProperties.dryRun()) {
            log.info("[{}] Dry run — would archive {} file(s)", contextName, candidates.size());
            return;
        }

        List<Future<?>> futures = new ArrayList<>();
        for (FileMetadata file : candidates) {
            futures.add(cleanupExecutorService.executor().submit(() -> archiveOne(contextName, file)));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ex) {
                log.error("[{}] One archive task failed: {}", contextName, ex.getMessage());
            }
        }

        log.info("[{}] Finished run at {}", contextName, Instant.now());
    }

    private void archiveOne(String contextName, FileMetadata file) {
        try {
            Path target = fileArchiveService.archiveFile(file);
            log.info("[{}] moved {} to {}", contextName, file.path().toAbsolutePath(), target.toAbsolutePath());
            auditService.logArchiveSuccess(file, target);
        } catch (Exception ex) {
            log.error("[{}] failed to archive {}: {}", contextName, file.path().toAbsolutePath(), ex.getMessage());
            auditService.logArchiveFailure(file, ex.getMessage());
        }
    }
}

