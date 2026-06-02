package com.autoarchive.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class AsyncAuditFileWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditFileWriter.class);

    private final Path auditFile;
    private final int batchSize;
    private final BlockingQueue<AuditLogEntry> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private volatile boolean running = true;
    private Thread workerThread;

    public AsyncAuditFileWriter(AuditProperties auditProperties) {
        this.auditFile = Path.of(auditProperties.filePath());
        this.batchSize = auditProperties.fileBatchSize();
    }

    @PostConstruct
    void startWorker() {
        workerThread = Thread.startVirtualThread(this::drainLoop);
        log.info("Async audit file writer started (batchSize={})", batchSize);
    }

    public void enqueue(AuditLogEntry entry) {
        queue.offer(entry);
        pendingCount.incrementAndGet();
    }

    public void awaitPendingWrites() throws InterruptedException {
        while (pendingCount.get() > 0 || !queue.isEmpty()) {
            Thread.sleep(10);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        running = false;
        if (workerThread != null) {
            workerThread.join(TimeUnit.SECONDS.toMillis(5));
        }
        awaitPendingWrites();
    }

    private void drainLoop() {
        List<AuditLogEntry> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                AuditLogEntry entry = queue.poll(200, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    batch.add(entry);
                }
                if (batch.size() >= batchSize || (entry == null && !batch.isEmpty() && queue.isEmpty())) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }

    private void flushBatch(List<AuditLogEntry> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(auditFile.getParent());
            StringBuilder lines = new StringBuilder(batch.size() * 128);
            for (AuditLogEntry entry : batch) {
                lines.append(formatLine(entry));
            }
            Files.writeString(
                    auditFile, lines.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            log.error("Failed batch audit file write ({} rows): {}", batch.size(), ex.getMessage());
        } finally {
            pendingCount.addAndGet(-batch.size());
        }
    }

    private static String formatLine(AuditLogEntry entry) {
        return String.format(
                "%s | %s | %s | file=%s | target=%s | bytes=%d | error=%s%n",
                entry.createdAt(),
                entry.actionType(),
                entry.status(),
                entry.filePath(),
                entry.targetPath() != null ? entry.targetPath() : "-",
                entry.bytesFreed(),
                entry.errorMessage() != null ? entry.errorMessage() : "-");
    }
}
