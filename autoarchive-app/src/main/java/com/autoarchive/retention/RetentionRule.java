package com.autoarchive.retention;

import java.util.List;

public record RetentionRule(
        Integer archiveAfterDays,
        List<String> extensions,
        Long minSizeBytes) {
}

