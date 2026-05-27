package com.autoarchive.scheduler;

import java.time.Instant;
import java.util.UUID;

public record CleanupJob(
        UUID id,
        UUID policyId,
        String jobName,
        String cronExpression,
        String timezone,
        Instant lastRunAt,
        Instant nextRunAt,
        CleanupJobStatus status) {
}

