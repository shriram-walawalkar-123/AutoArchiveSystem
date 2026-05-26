package com.autoarchive.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.autoarchive.storage.FileMetadata;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditProperties auditProperties;
    private final JdbcTemplate jdbcTemplate;

    public AuditService(AuditProperties auditProperties, JdbcTemplate jdbcTemplate) {
        this.auditProperties = auditProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void logArchiveSuccess(FileMetadata file, Path target) {
        AuditLogEntry entry = new AuditLogEntry(
                Instant.now(),
                AuditActionType.ARCHIVE,
                AuditStatus.SUCCESS,
                file.path().toAbsolutePath().toString(),
                target.toAbsolutePath().toString(),
                file.sizeBytes(),
                null);
        writeToFile(entry);
        writeToDatabase(entry);
    }

    public void logArchiveFailure(FileMetadata file, String errorMessage) {
        AuditLogEntry entry = new AuditLogEntry(
                Instant.now(),
                AuditActionType.ARCHIVE,
                AuditStatus.FAILED,
                file.path().toAbsolutePath().toString(),
                null,
                file.sizeBytes(),
                errorMessage);
        writeToFile(entry);
        writeToDatabase(entry);
    }

    private void writeToFile(AuditLogEntry entry) {
        try {
            Path auditFile = Path.of(auditProperties.filePath());
            Files.createDirectories(auditFile.getParent());
            String line = String.format(
                    "%s | %s | %s | file=%s | target=%s | bytes=%d | error=%s%n",
                    entry.createdAt(),
                    entry.actionType(),
                    entry.status(),
                    entry.filePath(),
                    entry.targetPath() != null ? entry.targetPath() : "-",
                    entry.bytesFreed(),
                    entry.errorMessage() != null ? entry.errorMessage() : "-");
            Files.writeString(
                    auditFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Audit logged to file: {}", auditFile.toAbsolutePath());
        } catch (IOException ex) {
            log.error("Failed to write audit log file: {}", ex.getMessage());
        }
    }

    private void writeToDatabase(AuditLogEntry entry) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO audit_logs (file_path, action_type, status, bytes_freed, error_message, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    entry.filePath(),
                    entry.actionType().name(),
                    entry.status().name(),
                    entry.bytesFreed(),
                    entry.errorMessage(),
                    Timestamp.from(entry.createdAt()));
            log.info("Audit logged to database for file: {}", entry.filePath());
        } catch (Exception ex) {
            log.error("Failed to write audit log to database: {}", ex.getMessage());
        }
    }
}