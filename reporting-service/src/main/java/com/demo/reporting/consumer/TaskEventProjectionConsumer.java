package com.demo.reporting.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskEvent;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportTaskRepository;
import com.demo.reporting.service.ReportPushService;
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
 * After each successful write the assigned user is notified via WebSocket push.
 */
@Component
public class TaskEventProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventProjectionConsumer.class);

    private final ReportTaskRepository repository;
    private final ObjectMapper objectMapper;
    private final ReportPushService pushService;

    public TaskEventProjectionConsumer(ReportTaskRepository repository,
                                       ObjectMapper objectMapper,
                                       ReportPushService pushService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.pushService = pushService;
    }

    /** Upserts / soft-deletes a {@link ReportTask} row based on the incoming event type. */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = "reporting-group", concurrency = "3")
    public void consume(String message) {
        try {
            TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
            log.debug("Received TaskEvent: task={} type={}", event.getTaskId(), event.getEventType());

            switch (event.getEventType()) {
                case CREATED, UPDATED -> upsert(event);
                case DELETED -> softDelete(event);
            }
        } catch (Exception e) {
            log.error("Failed to process task event: {}", e.getMessage(), e);
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
}
