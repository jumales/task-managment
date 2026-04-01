package com.demo.audit.model;

import com.demo.common.dto.WorkType;
import com.demo.common.event.TaskChangeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of a planned-work create operation,
 * written by the audit-service Kafka consumer.
 */
@Entity
@Table(name = "planned_work_audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedWorkAuditRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID taskId;
    private UUID plannedWorkId;

    /** Always PLANNED_WORK_CREATED. */
    @Enumerated(EnumType.STRING)
    private TaskChangeType changeType;

    /** User who set the planned hours. */
    private UUID plannedWorkUserId;

    @Enumerated(EnumType.STRING)
    private WorkType workType;

    private Integer plannedHours;

    private Instant changedAt;   // when the change happened in task-service
    private Instant recordedAt;  // when audit-service stored this record
}
