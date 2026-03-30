package com.demo.audit.model;

import com.demo.common.dto.WorkType;
import com.demo.common.event.TaskChangeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of a work log create, update, or delete operation,
 * written by the audit-service Kafka consumer.
 */
@Entity
@Table(name = "work_log_audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkLogAuditRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID taskId;
    private UUID workLogId;

    /** WORK_LOG_CREATED, WORK_LOG_UPDATED, or WORK_LOG_DELETED. */
    @Enumerated(EnumType.STRING)
    private TaskChangeType changeType;

    /** User who owns the work log entry; null for DELETED events. */
    private UUID workLogUserId;

    @Enumerated(EnumType.STRING)
    private WorkType workType;

    /** Null for DELETED events. */
    private Integer plannedHours;

    /** Null for DELETED events. */
    private Integer bookedHours;

    private Instant changedAt;   // when the change happened in task-service
    private Instant recordedAt;  // when audit-service stored this record
}
