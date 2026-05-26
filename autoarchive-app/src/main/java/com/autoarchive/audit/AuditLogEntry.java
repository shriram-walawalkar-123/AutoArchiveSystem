package com.autoarchive.audit;

import java.time.Instant;

public record AuditLogEntry(
        Instant createdAt,
        AuditActionType actionType,
        AuditStatus status,
        String filePath,
        String targetPath,
        long bytesFreed,
        String errorMessage) {
}
