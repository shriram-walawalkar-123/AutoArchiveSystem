package com.autoarchive.storage;

import com.autoarchive.config.ArchiveExecutionProperties.ExecutionMode;

public record ArchiveExecutionResult(
        ExecutionMode mode,
        int fileCount,
        int successCount,
        int failureCount,
        long durationMillis) {
}
