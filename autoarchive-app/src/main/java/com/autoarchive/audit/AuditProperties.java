package com.autoarchive.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoarchive.audit")
public record AuditProperties(
        String filePath,
        int dbBatchSize,
        int fileBatchSize,
        boolean fileEnabled,
        boolean dbEnabled) {
}
