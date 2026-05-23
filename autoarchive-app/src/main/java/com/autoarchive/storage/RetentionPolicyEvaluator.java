package com.autoarchive.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.autoarchive.config.RetentionProperties;

@Service
public class RetentionPolicyEvaluator {

    private final RetentionProperties retentionProperties;

    public RetentionPolicyEvaluator(RetentionProperties retentionProperties) {
        this.retentionProperties = retentionProperties;
    }

    public boolean isEligibleForArchive(FileMetadata file) {
        Instant cutoff = Instant.now()
                .minus(retentionProperties.archiveAfterDays(), ChronoUnit.DAYS);
        return file.lastModified().isBefore(cutoff);
    }

    public List<FileMetadata> findArchiveCandidates(List<FileMetadata> files) {
        return files.stream()
                .filter(this::isEligibleForArchive)
                .toList();
    }
}
