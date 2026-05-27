package com.autoarchive.scheduler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class CleanupJobScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobScheduler.class);

    private final CleanupJobRepository cleanupJobRepository;
    private final CleanupRunService cleanupRunService;
    private final CleanupExecutorService cleanupExecutorService;
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();

    public CleanupJobScheduler(
            CleanupJobRepository cleanupJobRepository,
            CleanupRunService cleanupRunService,
            CleanupExecutorService cleanupExecutorService) {
        this.cleanupJobRepository = cleanupJobRepository;
        this.cleanupRunService = cleanupRunService;
        this.cleanupExecutorService = cleanupExecutorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Poll DB for due jobs; execution is done on virtual threads.
        ticker.scheduleWithFixedDelay(this::tickSafe, 1, 5, TimeUnit.SECONDS);
        log.info("CleanupJobScheduler started (poll=5s)");
    }

    private void tickSafe() {
        try {
            tick();
        } catch (Exception ex) {
            log.error("Scheduler tick failed: {}", ex.getMessage());
        }
    }

    private void tick() {
        Instant now = Instant.now();
        var dueJobs = cleanupJobRepository.findDueIdleJobs(now, 10);
        for (CleanupJob job : dueJobs) {
            if (!cleanupJobRepository.tryMarkRunning(job.id(), now)) {
                continue;
            }
            cleanupExecutorService.executor().submit(() -> runJob(job));
        }
    }

    private void runJob(CleanupJob job) {
        Instant started = Instant.now();
        String context = "job=" + job.jobName();
        try {
            cleanupRunService.runOnce(context);

            Instant next = computeNextRun(job, started);
            cleanupJobRepository.markCompleted(job.id(), started, next, Instant.now());
            log.info("[{}] completed; next_run_at={}", context, next);
        } catch (Exception ex) {
            cleanupJobRepository.markFailed(job.id(), Instant.now());
            log.error("[{}] failed: {}", context, ex.getMessage());
        }
    }

    private Instant computeNextRun(CleanupJob job, Instant base) {
        ZoneId zone = ZoneId.of(job.timezone());
        CronExpression cron = CronExpression.parse(job.cronExpression());
        ZonedDateTime next = cron.next(ZonedDateTime.ofInstant(base, zone));
        if (next == null) {
            // If cron can't compute next (shouldn't happen), schedule far in future to avoid tight loops.
            return base.plusSeconds(3600);
        }
        return next.toInstant();
    }
}

