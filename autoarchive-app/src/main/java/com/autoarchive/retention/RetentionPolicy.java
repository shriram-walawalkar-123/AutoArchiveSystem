package com.autoarchive.retention;

import java.util.UUID;

public record RetentionPolicy(
        UUID id,
        String name,
        String region,
        boolean active,
        RetentionRule rule) {
}

