package com.demo.reporting.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.reporting.model.ReportBookedWork;
import com.demo.reporting.model.ReportPlannedWork;
import com.demo.reporting.repository.ReportBookedWorkRepository;
import com.demo.reporting.repository.ReportPlannedWorkRepository;
import com.demo.reporting.service.ReportPushService;
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
 * After each successful write the task's assigned user is notified via WebSocket push.
 */
@Component
public class TaskChangedProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskChangedProjectionConsumer.class);

    private final ReportPlannedWorkRepository plannedRepository;
    private final ReportBookedWorkRepository bookedRepository;
    private final ObjectMapper objectMapper;
    private final ReportPushService pushService;

    public TaskChangedProjectionConsumer(ReportPlannedWorkRepository plannedRepository,
                                         ReportBookedWorkRepository bookedRepository,
                                         ObjectMapper objectMapper,
                                         ReportPushService pushService) {
        this.plannedRepository = plannedRepository;
        this.bookedRepository = bookedRepository;
        this.objectMapper = objectMapper;
        this.pushService = pushService;
    }

    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = "reporting-group", concurrency = "3")
    public void consume(String message) {
        try {
            TaskChangedEvent event = objectMapper.readValue(message, TaskChangedEvent.class);
            log.debug("Received TaskChangedEvent: task={} changeType={}", event.getTaskId(), event.getChangeType());

            switch (event.getChangeType()) {
                case PLANNED_WORK_CREATED -> upsertPlannedWork(event);
                case BOOKED_WORK_CREATED, BOOKED_WORK_UPDATED -> upsertBookedWork(event);
                case BOOKED_WORK_DELETED -> softDeleteBookedWork(event);
                default -> { /* other change types are irrelevant to the hours report */ }
            }
        } catch (Exception e) {
            log.error("Failed to process TaskChangedEvent: {}", e.getMessage(), e);
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
        pushService.notifyUser(event.getAssignedUserId());
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
        pushService.notifyUser(event.getAssignedUserId());
    }

    private void softDeleteBookedWork(TaskChangedEvent event) {
        bookedRepository.findById(event.getWorkLogId()).ifPresent(row -> {
            bookedRepository.delete(row);
            pushService.notifyUser(event.getAssignedUserId());
        });
    }

    private static long toLong(BigInteger value) {
        return value == null ? 0L : value.longValueExact();
    }
}
