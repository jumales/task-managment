package com.demo.task.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly scheduler that triggers task archiving and expired archive table cleanup.
 * Runs at 02:00 (archive) and 03:00 (drop) to avoid overlapping with peak load.
 */
@Component
public class TaskArchiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskArchiveScheduler.class);

    private final TaskArchiveService archiveService;

    public TaskArchiveScheduler(TaskArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /** Moves expired closed tasks (and their related data) to the archive schema. */
    @Scheduled(cron = "0 0 2 * * *")
    public void archiveExpiredTasks() {
        log.info("Starting nightly task archive run");
        archiveService.archiveExpiredTasks();
        log.info("Nightly task archive run complete");
    }

    /** Drops archive tables whose age exceeds the configured retention period. */
    @Scheduled(cron = "0 0 3 * * *")
    public void dropExpiredArchiveTables() {
        log.info("Starting nightly archive table expiry run");
        archiveService.dropExpiredArchiveTables();
        log.info("Nightly archive table expiry run complete");
    }
}
