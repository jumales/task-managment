package com.demo.reporting.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.reporting.dedup.ProcessedEventService;
import com.demo.reporting.model.ReportBookedWork;
import com.demo.reporting.model.ReportPlannedWork;
import com.demo.reporting.repository.ReportBookedWorkRepository;
import com.demo.reporting.repository.ReportPlannedWorkRepository;
import com.demo.reporting.service.ReportPushService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Consumes {@link TaskChangedEvent} messages from the {@code task-changed} topic and maintains
 * the reporting planned/booked work projections. Uses JSON-over-String so it shares the same
 * {@code StringDeserializer} container factory as {@link TaskEventProjectionConsumer}.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 * After each successful write the task's assigned user is notified via WebSocket push.
 */
@Component
public class TaskChangedProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskChangedProjectionConsumer.class);

    /** Kafka consumer group — paired with the {@link #consume} @KafkaListener groupId. */
    private static final String CONSUMER_GROUP = "reporting-group";

    private final ReportPlannedWorkRepository plannedRepository;
    private final ReportBookedWorkRepository bookedRepository;
    private final ObjectMapper objectMapper;
    private final ReportPushService pushService;
    private final ProcessedEventService processedEventService;

    public TaskChangedProjectionConsumer(ReportPlannedWorkRepository plannedRepository,
                                         ReportBookedWorkRepository bookedRepository,
                                         ObjectMapper objectMapper,
                                         ReportPushService pushService,
                                         ProcessedEventService processedEventService) {
        this.plannedRepository = plannedRepository;
        this.bookedRepository = bookedRepository;
        this.objectMapper = objectMapper;
        this.pushService = pushService;
        this.processedEventService = processedEventService;
    }

    /**
     * Receives a task changed event from Kafka and updates the reporting planned/booked work projections.
     *
     * <p>Idempotency: {@link ProcessedEventService#markProcessed} prevents duplicate WebSocket pushes and
     * repeated upserts on events re-delivered after an outbox publisher crash.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT immediately
     */
    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = CONSUMER_GROUP, concurrency = "3")
    public void consume(String message) throws JsonProcessingException {
        TaskChangedEvent event = objectMapper.readValue(message, TaskChangedEvent.class);
        log.debug("Received TaskChangedEvent: task={} changeType={}", event.getTaskId(), event.getChangeType());

        if (event.getEventId() != null
                && !processedEventService.markProcessed(event.getEventId(), CONSUMER_GROUP)) {
            log.info("Duplicate event {} — skipping", event.getEventId());
            return;
        }

        switch (event.getChangeType()) {
            case PLANNED_WORK_CREATED                    -> upsertPlannedWork(event);
            case BOOKED_WORK_CREATED, BOOKED_WORK_UPDATED -> upsertBookedWork(event);
            case BOOKED_WORK_DELETED                     -> softDeleteBookedWork(event);
            default -> { /* other change types are irrelevant to the hours report */ }
        }
    }

    private void upsertPlannedWork(TaskChangedEvent event) {
        ReportPlannedWork row = plannedRepository.findById(event.getWorkLogId()).orElseGet(ReportPlannedWork::new);
        row.setId(event.getWorkLogId());
        row.setTaskId(event.getTaskId());
        row.setProjectId(event.getProjectId());
        row.setUserId(event.getWorkLogUserId());
        row.setWorkType(event.getWorkType());
        row.setPlannedHours(toLong(event.getPlannedHours()));
        row.setUpdatedAt(event.getChangedAt() != null ? event.getChangedAt() : Instant.now());
        plannedRepository.save(row);
        pushService.notifyUser(event.getWorkLogUserId());
    }

    private void upsertBookedWork(TaskChangedEvent event) {
        ReportBookedWork row = bookedRepository.findById(event.getWorkLogId()).orElseGet(ReportBookedWork::new);
        row.setId(event.getWorkLogId());
        row.setTaskId(event.getTaskId());
        row.setProjectId(event.getProjectId());
        row.setUserId(event.getWorkLogUserId());
        row.setWorkType(event.getWorkType());
        row.setBookedHours(toLong(event.getBookedHours()));
        row.setUpdatedAt(event.getChangedAt() != null ? event.getChangedAt() : Instant.now());
        bookedRepository.save(row);
        pushService.notifyUser(event.getWorkLogUserId());
    }

    private void softDeleteBookedWork(TaskChangedEvent event) {
        bookedRepository.findById(event.getWorkLogId()).ifPresent(row -> {
            bookedRepository.delete(row);
            pushService.notifyUser(event.getWorkLogUserId());
        });
    }

    private static long toLong(BigInteger value) {
        return value == null ? 0L : value.longValueExact();
    }
}
