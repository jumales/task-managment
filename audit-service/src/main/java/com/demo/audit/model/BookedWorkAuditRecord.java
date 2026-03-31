package com.demo.audit.model;

import com.demo.common.dto.WorkType;
import com.demo.common.event.TaskChangeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of a booked-work create, update, or delete operation,
 * written by the audit-service Kafka consumer.
 */
@Entity
@Table(name = "booked_work_audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookedWorkAuditRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID taskId;
    private UUID bookedWorkId;

    /** BOOKED_WORK_CREATED, BOOKED_WORK_UPDATED, or BOOKED_WORK_DELETED. */
    @Enumerated(EnumType.STRING)
    private TaskChangeType changeType;

    /** User who booked the hours; null for DELETED events. */
    private UUID bookedWorkUserId;

    @Enumerated(EnumType.STRING)
    private WorkType workType;

    /** Null for DELETED events. */
    private Integer bookedHours;

    private Instant changedAt;   // when the change happened in task-service
    private Instant recordedAt;  // when audit-service stored this record
}
