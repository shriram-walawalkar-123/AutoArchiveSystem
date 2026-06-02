package com.autoarchive.audit;

import java.nio.file.Path;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.autoarchive.storage.FileMetadata;

@Service
public class AuditService {

    private final AuditProperties auditProperties;
    private final AsyncAuditFileWriter asyncAuditFileWriter;
    private final AsyncAuditDbWriter asyncAuditDbWriter;

    public AuditService(
            AuditProperties auditProperties,
            AsyncAuditFileWriter asyncAuditFileWriter,
            AsyncAuditDbWriter asyncAuditDbWriter) {
        this.auditProperties = auditProperties;
        this.asyncAuditFileWriter = asyncAuditFileWriter;
        this.asyncAuditDbWriter = asyncAuditDbWriter;
    }

    public void logArchiveSuccess(FileMetadata file, Path target) {
        enqueue(new AuditLogEntry(
                Instant.now(),
                AuditActionType.ARCHIVE,
                AuditStatus.SUCCESS,
                file.path().toAbsolutePath().toString(),
                target.toAbsolutePath().toString(),
                file.sizeBytes(),
                null));
    }

    public void logArchiveFailure(FileMetadata file, String errorMessage) {
        enqueue(new AuditLogEntry(
                Instant.now(),
                AuditActionType.ARCHIVE,
                AuditStatus.FAILED,
                file.path().toAbsolutePath().toString(),
                null,
                file.sizeBytes(),
                errorMessage));
    }

    public void awaitPendingWrites() throws InterruptedException {
        if (auditProperties.fileEnabled()) {
            asyncAuditFileWriter.awaitPendingWrites();
        }
        if (auditProperties.dbEnabled()) {
            asyncAuditDbWriter.awaitPendingWrites();
        }
    }

    private void enqueue(AuditLogEntry entry) {
        if (auditProperties.fileEnabled()) {
            asyncAuditFileWriter.enqueue(entry);
        }
        if (auditProperties.dbEnabled()) {
            asyncAuditDbWriter.enqueue(entry);
        }
    }
}
