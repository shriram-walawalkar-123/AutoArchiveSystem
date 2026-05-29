package com.autoarchive.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.autoarchive.audit.AuditService;
import com.autoarchive.config.ArchiveExecutionProperties.ExecutionMode;

@Service
public class ArchivePipelineService {

    private static final Logger log = LoggerFactory.getLogger(ArchivePipelineService.class);

    private final FileArchiveService fileArchiveService;
    private final AuditService auditService;
    private final VirtualThreadArchiveExecutor virtualThreadArchiveExecutor;

    public ArchivePipelineService(
            FileArchiveService fileArchiveService,
            AuditService auditService,
            VirtualThreadArchiveExecutor virtualThreadArchiveExecutor) {
        this.fileArchiveService = fileArchiveService;
        this.auditService = auditService;
        this.virtualThreadArchiveExecutor = virtualThreadArchiveExecutor;
    }

    public ArchiveExecutionResult execute(List<FileMetadata> files, ExecutionMode mode) {
        long startNanos = System.nanoTime();
        int success = 0;
        int failed = 0;

        if (mode == ExecutionMode.SEQUENTIAL) {
            for (FileMetadata file : files) {
                if (archiveOne(file)) {
                    success++;
                } else {
                    failed++;
                }
            }
        } else {
            List<Future<Boolean>> futures = new ArrayList<>();
            try (ExecutorService executor = virtualThreadArchiveExecutor.newExecutor()) {
                for (FileMetadata file : files) {
                    futures.add(executor.submit(() -> archiveOne(file)));
                }
                for (Future<Boolean> future : futures) {
                    try {
                        if (Boolean.TRUE.equals(future.get())) {
                            success++;
                        } else {
                            failed++;
                        }
                    } catch (Exception ex) {
                        failed++;
                        log.error("Parallel archive task failed: {}", ex.getMessage());
                    }
                }
            }
        }

        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        return new ArchiveExecutionResult(mode, files.size(), success, failed, durationMillis);
    }

    private boolean archiveOne(FileMetadata file) {
        try {
            var target = fileArchiveService.archiveFile(file);
            auditService.logArchiveSuccess(file, target);
            return true;
        } catch (Exception ex) {
            log.error("Failed to archive {}: {}", file.path().toAbsolutePath(), ex.getMessage());
            auditService.logArchiveFailure(file, ex.getMessage());
            return false;
        }
    }
}
