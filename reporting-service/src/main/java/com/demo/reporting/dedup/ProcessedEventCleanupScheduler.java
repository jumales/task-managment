package com.demo.reporting.dedup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes old dedup records from {@code processed_kafka_events} to prevent unbounded table growth.
 * Retention window must exceed the Kafka topic retention so no live event can be replayed
 * after its dedup record is removed.
 */
@Component
public class ProcessedEventCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanupScheduler.class);

    @Value("${ttl.processed-events.retention-days:30}")
    private int retentionDays;

    private final ProcessedEventRepository repository;

    public ProcessedEventCleanupScheduler(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /** Deletes dedup records older than {@code ttl.processed-events.retention-days}. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("Deleted {} processed event dedup record(s) older than {} days", deleted, retentionDays);
    }
}
