package com.autoarchive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoarchive.retention")
public record RetentionProperties(int archiveAfterDays, boolean dryRun) {
}