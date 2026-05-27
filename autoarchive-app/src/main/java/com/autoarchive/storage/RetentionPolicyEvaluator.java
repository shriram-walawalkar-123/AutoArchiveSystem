package com.autoarchive.storage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.autoarchive.config.RetentionProperties;
import com.autoarchive.retention.RetentionPolicy;
import com.autoarchive.retention.RetentionPolicyRepository;
import com.autoarchive.retention.RetentionRule;

@Service
public class RetentionPolicyEvaluator {

    private final RetentionProperties retentionProperties;
    private final RetentionPolicyRepository retentionPolicyRepository;

    public RetentionPolicyEvaluator(
            RetentionProperties retentionProperties,
            RetentionPolicyRepository retentionPolicyRepository) {
        this.retentionProperties = retentionProperties;
        this.retentionPolicyRepository = retentionPolicyRepository;
    }

    public boolean isEligibleForArchive(FileMetadata file) {
        // v1 behavior: if DB has active policies, evaluate against them; otherwise fall back to application.yml
        List<RetentionPolicy> policies = retentionPolicyRepository.findActivePolicies();
        if (policies.isEmpty()) {
            return isEligibleByConfig(file);
        }
        return policies.stream().anyMatch(p -> matchesRule(file, p.rule()));
    }

    public List<FileMetadata> findArchiveCandidates(List<FileMetadata> files) {
        return files.stream()
                .filter(this::isEligibleForArchive)
                .toList();
    }

    private boolean isEligibleByConfig(FileMetadata file) {
        Instant cutoff = Instant.now()
                .minus(retentionProperties.archiveAfterDays(), ChronoUnit.DAYS);
        return file.lastModified().isBefore(cutoff);
    }

    private boolean matchesRule(FileMetadata file, RetentionRule rule) {
        int days = rule.archiveAfterDays() != null ? rule.archiveAfterDays() : retentionProperties.archiveAfterDays();
        Instant cutoff = Instant.now().atZone(ZoneId.of("UTC")).toInstant()
                .minus(days, ChronoUnit.DAYS);
        if (!file.lastModified().isBefore(cutoff)) {
            return false;
        }

        if (rule.minSizeBytes() != null && file.sizeBytes() < rule.minSizeBytes()) {
            return false;
        }

        if (rule.extensions() == null || rule.extensions().isEmpty()) {
            return true;
        }

        String ext = getExtensionLower(file);
        if (ext == null) {
            return false;
        }

        return rule.extensions().stream()
                .filter(Objects::nonNull)
                .map(e -> e.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""))
                .anyMatch(e -> e.equals(ext));
    }

    private String getExtensionLower(FileMetadata file) {
        String name = file.path().getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
