package com.demo.notification.scheduler;

import com.demo.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes notification records that are older than the configured retention period.
 * Notifications are append-only history — older records have no operational value.
 */
@Component
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);

    @Value("${ttl.notification.retention-days:365}")
    private int retentionDays;

    private final NotificationRepository repository;

    public NotificationCleanupScheduler(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Deletes notification records older than {@code ttl.notification.retention-days}. */
    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("Deleted {} notification record(s) older than {} days", deleted, retentionDays);
    }
}
