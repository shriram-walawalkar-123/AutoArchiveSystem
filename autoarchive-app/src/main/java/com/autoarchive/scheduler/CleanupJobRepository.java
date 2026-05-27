package com.autoarchive.scheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CleanupJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public CleanupJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CleanupJob> findDueIdleJobs(Instant now, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, policy_id, job_name, cron_expression, timezone, last_run_at, next_run_at, status
                FROM cleanup_jobs
                WHERE status = 'IDLE' AND next_run_at IS NOT NULL AND next_run_at <= ?
                ORDER BY next_run_at ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new CleanupJob(
                        rs.getObject("id", UUID.class),
                        rs.getObject("policy_id", UUID.class),
                        rs.getString("job_name"),
                        rs.getString("cron_expression"),
                        rs.getString("timezone"),
                        rs.getTimestamp("last_run_at") != null ? rs.getTimestamp("last_run_at").toInstant() : null,
                        rs.getTimestamp("next_run_at") != null ? rs.getTimestamp("next_run_at").toInstant() : null,
                        CleanupJobStatus.valueOf(rs.getString("status"))),
                Timestamp.from(now),
                limit);
    }

    public boolean tryMarkRunning(UUID jobId, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE cleanup_jobs
                SET status='RUNNING', updated_at=?
                WHERE id=? AND status='IDLE'
                """,
                Timestamp.from(now),
                jobId);
        return updated == 1;
    }

    public void markCompleted(UUID jobId, Instant lastRunAt, Instant nextRunAt, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE cleanup_jobs
                SET status='IDLE', last_run_at=?, next_run_at=?, updated_at=?
                WHERE id=?
                """,
                Timestamp.from(lastRunAt),
                Timestamp.from(nextRunAt),
                Timestamp.from(now),
                jobId);
    }

    public void markFailed(UUID jobId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE cleanup_jobs
                SET status='FAILED', updated_at=?
                WHERE id=?
                """,
                Timestamp.from(now),
                jobId);
    }
}

