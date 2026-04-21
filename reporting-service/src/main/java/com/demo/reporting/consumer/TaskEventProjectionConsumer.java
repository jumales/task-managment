package com.demo.reporting.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskEvent;
import com.demo.reporting.dedup.ProcessedEventService;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportBookedWorkRepository;
import com.demo.reporting.repository.ReportPlannedWorkRepository;
import com.demo.reporting.repository.ReportTaskRepository;
import com.demo.reporting.service.ReportPushService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes task lifecycle events from the {@code task-events} topic and maintains the
 * reporting read-model ({@link ReportTask}). Uses the same JSON-over-String pattern as
 * search-service so the listener is decoupled from any spring-kafka default-type config.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 * After each successful write the assigned user is notified via WebSocket push.
 */
@Component
public class TaskEventProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventProjectionConsumer.class);

    /** Kafka consumer group — paired with the {@link #consume} @KafkaListener groupId. */
    private static final String CONSUMER_GROUP = "reporting-group";

    private final ReportTaskRepository repository;
    private final ReportBookedWorkRepository bookedWorkRepository;
    private final ReportPlannedWorkRepository plannedWorkRepository;
    private final ObjectMapper objectMapper;
    private final ReportPushService pushService;
    private final ProcessedEventService processedEventService;

    public TaskEventProjectionConsumer(ReportTaskRepository repository,
                                       ReportBookedWorkRepository bookedWorkRepository,
                                       ReportPlannedWorkRepository plannedWorkRepository,
                                       ObjectMapper objectMapper,
                                       ReportPushService pushService,
                                       ProcessedEventService processedEventService) {
        this.repository = repository;
        this.bookedWorkRepository = bookedWorkRepository;
        this.plannedWorkRepository = plannedWorkRepository;
        this.objectMapper = objectMapper;
        this.pushService = pushService;
        this.processedEventService = processedEventService;
    }

    /**
     * Upserts / soft-deletes a {@link ReportTask} row based on the incoming event type.
     *
     * <p>Idempotency: {@link ProcessedEventService#markProcessed} prevents a duplicate WebSocket push
     * notification on events re-delivered after an outbox publisher crash.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT immediately
     */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = CONSUMER_GROUP, concurrency = "3")
    public void consume(String message) throws JsonProcessingException {
        TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
        log.debug("Received TaskEvent: task={} type={}", event.getTaskId(), event.getEventType());

        if (event.getEventId() != null
                && !processedEventService.markProcessed(event.getEventId(), CONSUMER_GROUP)) {
            log.info("Duplicate event {} — skipping", event.getEventId());
            return;
        }

        switch (event.getEventType()) {
            case CREATED, UPDATED -> upsert(event);
            case DELETED          -> softDelete(event);
            case ARCHIVED         -> deleteProjections(event);
        }
    }

    private void upsert(TaskEvent event) {
        ReportTask existing = repository.findById(event.getTaskId()).orElseGet(ReportTask::new);
        existing.setId(event.getTaskId());
        existing.setTaskCode(event.getTaskCode());
        existing.setTitle(event.getTitle());
        existing.setDescription(event.getDescription());
        existing.setStatus(event.getStatus());
        existing.setProjectId(event.getProjectId());
        existing.setProjectName(event.getProjectName());
        existing.setPhaseId(event.getPhaseId());
        existing.setPhaseName(event.getPhaseName());
        existing.setAssignedUserId(event.getAssignedUserId());
        existing.setAssignedUserName(event.getAssignedUserName());
        existing.setPlannedStart(event.getPlannedStart());
        existing.setPlannedEnd(event.getPlannedEnd());
        existing.setUpdatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
        repository.save(existing);
        pushService.notifyUser(event.getAssignedUserId());
    }

    private void softDelete(TaskEvent event) {
        repository.findById(event.getTaskId()).ifPresent(task -> {
            repository.delete(task);
            pushService.notifyUser(task.getAssignedUserId());
        });
    }

    /**
     * Hard-deletes all reporting projections for an archived task.
     * Projections are read-model replicas — deleting them frees space without data loss.
     */
    @org.springframework.transaction.annotation.Transactional
    private void deleteProjections(TaskEvent event) {
        bookedWorkRepository.deleteAllByTaskId(event.getTaskId());
        plannedWorkRepository.deleteAllByTaskId(event.getTaskId());
        repository.findById(event.getTaskId()).ifPresent(task -> {
            repository.delete(task);
            pushService.notifyUser(task.getAssignedUserId());
        });
        log.debug("Deleted reporting projections for archived task={}", event.getTaskId());
    }
}
