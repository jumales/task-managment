package com.demo.task.archive;

import com.demo.task.config.TtlProperties;
import com.demo.task.repository.TaskCodeJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes processed task_code_jobs rows that are older than the configured retention period.
 * Prevents the {@code task_code_jobs} table from growing unbounded as processed rows accumulate.
 */
@Service
public class TaskCodeJobCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TaskCodeJobCleanupService.class);

    private final TaskCodeJobRepository taskCodeJobRepository;
    private final TtlProperties ttlProperties;

    public TaskCodeJobCleanupService(TaskCodeJobRepository taskCodeJobRepository, TtlProperties ttlProperties) {
        this.taskCodeJobRepository = taskCodeJobRepository;
        this.ttlProperties = ttlProperties;
    }

    /** Deletes processed task_code_jobs rows older than {@code ttl.task-code-job.retention-days}. */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void cleanup() {
        int days = ttlProperties.getTaskCodeJob().getRetentionDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int deleted = taskCodeJobRepository.deleteProcessedOlderThan(cutoff);
        log.info("Deleted {} processed task_code_jobs row(s) older than {} days", deleted, days);
    }
}
