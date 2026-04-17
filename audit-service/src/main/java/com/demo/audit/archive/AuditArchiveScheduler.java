package com.demo.audit.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly scheduler that drops expired audit archive tables.
 * The archive-and-delete step is triggered by {@link com.demo.audit.consumer.TaskLifecycleConsumer}
 * on receipt of each TASK_ARCHIVED Kafka event.
 */
@Component
public class AuditArchiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveScheduler.class);

    private final AuditArchiveService archiveService;

    public AuditArchiveScheduler(AuditArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /** Drops audit archive tables whose age exceeds the configured retention period. */
    @Scheduled(cron = "0 30 3 * * *")
    public void dropExpiredArchiveTables() {
        log.info("Starting nightly audit archive table expiry run");
        archiveService.dropExpiredArchiveTables();
        log.info("Nightly audit archive table expiry run complete");
    }
}
