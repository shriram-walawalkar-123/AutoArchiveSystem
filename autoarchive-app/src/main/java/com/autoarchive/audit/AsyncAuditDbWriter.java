package com.autoarchive.audit;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class AsyncAuditDbWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditDbWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final BlockingQueue<AuditLogEntry> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private volatile boolean running = true;
    private Thread workerThread;

    public AsyncAuditDbWriter(JdbcTemplate jdbcTemplate, AuditProperties auditProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = auditProperties.dbBatchSize();
    }

    @PostConstruct
    void startWorker() {
        workerThread = Thread.startVirtualThread(this::drainLoop);
        log.info("Async audit DB writer started (batchSize={})", batchSize);
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
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO audit_logs (file_path, action_type, status, bytes_freed, error_message, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            AuditLogEntry entry = batch.get(i);
                            ps.setString(1, entry.filePath());
                            ps.setString(2, entry.actionType().name());
                            ps.setString(3, entry.status().name());
                            ps.setLong(4, entry.bytesFreed());
                            ps.setString(5, entry.errorMessage());
                            ps.setTimestamp(6, Timestamp.from(entry.createdAt()));
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    });
            log.debug("Flushed {} audit row(s) to database", batch.size());
        } catch (Exception ex) {
            log.error("Failed batch audit DB write ({} rows): {}", batch.size(), ex.getMessage());
        } finally {
            pendingCount.addAndGet(-batch.size());
        }
    }
}
