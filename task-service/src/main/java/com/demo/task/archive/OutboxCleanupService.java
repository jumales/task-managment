package com.demo.task.archive;

import com.demo.task.config.TtlProperties;
import com.demo.task.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes published outbox events that are older than the configured retention period.
 * Prevents the {@code outbox_events} table from growing unbounded as events accumulate.
 */
@Service
public class OutboxCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupService.class);

    private final OutboxRepository outboxRepository;
    private final TtlProperties ttlProperties;

    public OutboxCleanupService(OutboxRepository outboxRepository, TtlProperties ttlProperties) {
        this.outboxRepository = outboxRepository;
        this.ttlProperties = ttlProperties;
    }

    /** Deletes published outbox events older than {@code ttl.outbox.retention-days}. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanup() {
        int days = ttlProperties.getOutbox().getRetentionDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int deleted = outboxRepository.deletePublishedOlderThan(cutoff);
        log.info("Deleted {} published outbox event(s) older than {} days", deleted, days);
    }
}
