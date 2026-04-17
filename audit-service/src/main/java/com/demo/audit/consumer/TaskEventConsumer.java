package com.demo.audit.consumer;

import com.demo.audit.model.StatusAuditRecord;
import com.demo.audit.model.BookedWorkAuditRecord;
import com.demo.audit.model.CommentAuditRecord;
import com.demo.audit.model.PhaseAuditRecord;
import com.demo.audit.model.PlannedWorkAuditRecord;
import com.demo.audit.repository.AuditRepository;
import com.demo.audit.repository.BookedWorkAuditRepository;
import com.demo.audit.repository.CommentAuditRepository;
import com.demo.audit.repository.PhaseAuditRepository;
import com.demo.audit.repository.PlannedWorkAuditRepository;
import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Single consumer for all task change events.
 * Routes each event to the appropriate audit store based on {@link TaskChangedEvent#getChangeType()}.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 */
@Component
public class TaskEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventConsumer.class);

    private final AuditRepository auditRepository;
    private final CommentAuditRepository commentAuditRepository;
    private final PhaseAuditRepository phaseAuditRepository;
    private final PlannedWorkAuditRepository plannedWorkAuditRepository;
    private final BookedWorkAuditRepository bookedWorkAuditRepository;

    public TaskEventConsumer(AuditRepository auditRepository,
                             CommentAuditRepository commentAuditRepository,
                             PhaseAuditRepository phaseAuditRepository,
                             PlannedWorkAuditRepository plannedWorkAuditRepository,
                             BookedWorkAuditRepository bookedWorkAuditRepository) {
        this.auditRepository = auditRepository;
        this.commentAuditRepository = commentAuditRepository;
        this.phaseAuditRepository = phaseAuditRepository;
        this.plannedWorkAuditRepository = plannedWorkAuditRepository;
        this.bookedWorkAuditRepository = bookedWorkAuditRepository;
    }

    /** Receives a task change event from Kafka and routes it to the appropriate audit store. */
    @KafkaListener(topics = KafkaTopics.TASK_CHANGED, groupId = "audit-group", concurrency = "12")
    public void consume(TaskChangedEvent event) {
        log.info("Received TaskChangedEvent: task={} changeType={}", event.getTaskId(), event.getChangeType());
        switch (event.getChangeType()) {
            case STATUS_CHANGED       -> persistStatusChange(event);
            case COMMENT_ADDED        -> persistCommentChange(event);
            case PHASE_CHANGED        -> persistPhaseChange(event);
            case PLANNED_WORK_CREATED -> persistPlannedWorkChange(event);
            case BOOKED_WORK_CREATED,
                 BOOKED_WORK_UPDATED,
                 BOOKED_WORK_DELETED  -> persistBookedWorkChange(event);
        }
    }

    private void persistStatusChange(TaskChangedEvent event) {
        auditRepository.save(StatusAuditRecord.builder()
                .taskId(event.getTaskId())
                .assignedUserId(event.getAssignedUserId())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .changedAt(event.getChangedAt())
                .recordedAt(Instant.now())
                .build());
    }

    private void persistCommentChange(TaskChangedEvent event) {
        commentAuditRepository.save(CommentAuditRecord.builder()
                .taskId(event.getTaskId())
                .commentCreatedByUserId(event.getAssignedUserId())
                .commentId(event.getCommentId())
                .content(event.getCommentContent())
                .addedAt(event.getChangedAt())
                .recordedAt(Instant.now())
                .build());
    }

    private void persistPhaseChange(TaskChangedEvent event) {
        phaseAuditRepository.save(PhaseAuditRecord.builder()
                .taskId(event.getTaskId())
                .changedByUserId(event.getAssignedUserId())
                .fromPhaseId(event.getFromPhaseId())
                .fromPhaseName(event.getFromPhaseName())
                .toPhaseId(event.getToPhaseId())
                .toPhaseName(event.getToPhaseName())
                .changedAt(event.getChangedAt())
                .recordedAt(Instant.now())
                .build());
    }

    private void persistPlannedWorkChange(TaskChangedEvent event) {
        plannedWorkAuditRepository.save(PlannedWorkAuditRecord.builder()
                .taskId(event.getTaskId())
                .plannedWorkId(event.getWorkLogId())
                .changeType(event.getChangeType())
                .plannedWorkUserId(event.getWorkLogUserId())
                .workType(event.getWorkType())
                .plannedHours(event.getPlannedHours() != null ? event.getPlannedHours().intValue() : null)
                .changedAt(event.getChangedAt())
                .recordedAt(Instant.now())
                .build());
    }

    private void persistBookedWorkChange(TaskChangedEvent event) {
        bookedWorkAuditRepository.save(BookedWorkAuditRecord.builder()
                .taskId(event.getTaskId())
                .bookedWorkId(event.getWorkLogId())
                .changeType(event.getChangeType())
                .bookedWorkUserId(event.getWorkLogUserId())
                .workType(event.getWorkType())
                .bookedHours(event.getBookedHours() != null ? event.getBookedHours().intValue() : null)
                .changedAt(event.getChangedAt())
                .recordedAt(Instant.now())
                .build());
    }
}
