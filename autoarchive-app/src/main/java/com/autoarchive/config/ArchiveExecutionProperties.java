package com.autoarchive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoarchive.archive")
public record ArchiveExecutionProperties(
        ExecutionMode mode,
        int parallelConcurrency,
        int batchSize) {

    public enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL
    }
}
