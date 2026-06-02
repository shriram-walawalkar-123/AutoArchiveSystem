package com.autoarchive.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.autoarchive.audit.AuditService;
import com.autoarchive.config.ArchiveExecutionProperties;
import com.autoarchive.config.ArchiveExecutionProperties.ExecutionMode;

@Service
public class ArchivePipelineService {

    private static final Logger log = LoggerFactory.getLogger(ArchivePipelineService.class);

    private final FileArchiveService fileArchiveService;
    private final AuditService auditService;
    private final VirtualThreadArchiveExecutor virtualThreadArchiveExecutor;
    private final ArchiveExecutionProperties archiveExecutionProperties;

    public ArchivePipelineService(
            FileArchiveService fileArchiveService,
            AuditService auditService,
            VirtualThreadArchiveExecutor virtualThreadArchiveExecutor,
            ArchiveExecutionProperties archiveExecutionProperties) {
        this.fileArchiveService = fileArchiveService;
        this.auditService = auditService;
        this.virtualThreadArchiveExecutor = virtualThreadArchiveExecutor;
        this.archiveExecutionProperties = archiveExecutionProperties;
    }

    public ArchiveExecutionResult execute(List<FileMetadata> files, ExecutionMode mode) throws InterruptedException {
        long startNanos = System.nanoTime();
        int success;
        int failed;
        ConcurrentLinkedQueue<PendingAudit> pendingAudits = null;

        if (mode == ExecutionMode.SEQUENTIAL) {
            int[] counts = executeSequential(files);
            success = counts[0];
            failed = counts[1];
        } else {
            MoveResult moveResult = executeParallelMoves(files);
            success = moveResult.success();
            failed = moveResult.failed();
            pendingAudits = moveResult.pendingAudits();
        }

        long moveDoneNanos = System.nanoTime();

        if (pendingAudits != null) {
            flushAudits(pendingAudits);
        }

        long auditEnqueueDoneNanos = System.nanoTime();

        auditService.awaitPendingWrites();

        long totalMillis = (System.nanoTime() - startNanos) / 1_000_000;
        long moveMillis = (moveDoneNanos - startNanos) / 1_000_000;
        long auditEnqueueMillis = (auditEnqueueDoneNanos - moveDoneNanos) / 1_000_000;
        long auditFlushMillis = (System.nanoTime() - auditEnqueueDoneNanos) / 1_000_000;

        log.info(
                "Timing breakdown | mode={} | move={} ms | audit-enqueue={} ms | audit-flush={} ms | total={} ms",
                mode,
                moveMillis,
                auditEnqueueMillis,
                auditFlushMillis,
                totalMillis);

        return new ArchiveExecutionResult(mode, files.size(), success, failed, totalMillis);
    }

    private int[] executeSequential(List<FileMetadata> files) {
        int success = 0;
        int failed = 0;
        for (FileMetadata file : files) {
            if (archiveOne(file)) {
                success++;
            } else {
                failed++;
            }
        }
        return new int[] {success, failed};
    }

    /**
     * Parallel file moves into shard subfolders (one folder per worker) so threads do not
     * fight over the same Windows directory lock. Audit is recorded after all moves finish.
     */
    private MoveResult executeParallelMoves(List<FileMetadata> files) throws InterruptedException {
        int workers = Math.max(1, archiveExecutionProperties.parallelConcurrency());
        AtomicInteger nextIndex = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ConcurrentLinkedQueue<PendingAudit> pendingAudits = new ConcurrentLinkedQueue<>();
        CountDownLatch done = new CountDownLatch(workers);

        log.info(
                "Parallel move started | workers={} | shards={} | layout=archive/shard-{{0..{}}}/",
                workers,
                workers,
                workers - 1);

        try (ExecutorService executor = virtualThreadArchiveExecutor.newExecutor()) {
            for (int w = 0; w < workers; w++) {
                executor.submit(() -> {
                    try {
                        while (true) {
                            int index = nextIndex.getAndIncrement();
                            if (index >= files.size()) {
                                break;
                            }
                            FileMetadata file = files.get(index);
                            try {
                                Path target = fileArchiveService.archiveFileSharded(file, index, workers);
                                pendingAudits.add(new PendingAudit(file, target, null));
                                success.incrementAndGet();
                            } catch (Exception ex) {
                                pendingAudits.add(new PendingAudit(file, null, ex.getMessage()));
                                failed.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
        }

        return new MoveResult(success.get(), failed.get(), pendingAudits);
    }

    private void flushAudits(ConcurrentLinkedQueue<PendingAudit> pendingAudits) {
        for (PendingAudit pending : pendingAudits) {
            if (pending.errorMessage() == null) {
                auditService.logArchiveSuccess(pending.file(), pending.target());
            } else {
                auditService.logArchiveFailure(pending.file(), pending.errorMessage());
            }
        }
    }

    private boolean archiveOne(FileMetadata file) {
        try {
            Path target = fileArchiveService.archiveFile(file);
            auditService.logArchiveSuccess(file, target);
            return true;
        } catch (Exception ex) {
            log.error("Failed to archive {}: {}", file.path().toAbsolutePath(), ex.getMessage());
            auditService.logArchiveFailure(file, ex.getMessage());
            return false;
        }
    }

    private record PendingAudit(FileMetadata file, Path target, String errorMessage) {}

    private record MoveResult(int success, int failed, ConcurrentLinkedQueue<PendingAudit> pendingAudits) {}
}
