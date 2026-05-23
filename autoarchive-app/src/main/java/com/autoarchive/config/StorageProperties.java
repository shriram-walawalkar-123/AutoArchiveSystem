package com.autoarchive.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoarchive.storage")
public record StorageProperties(
        String type,
        List<String> scanRoots,
        String archiveRoot) {
}
