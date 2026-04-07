package com.demo.audit.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a phase change on a task, written by the audit-service Kafka consumer.
 */
@Entity
@Table(name = "phase_audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhaseAuditRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID taskId;
    /** ID of the user who performed the phase change (not the task assignee). */
    private UUID changedByUserId;

    private UUID fromPhaseId;
    private String fromPhaseName;
    private UUID toPhaseId;
    private String toPhaseName;

    private Instant changedAt;   // when the phase change happened in task-service
    private Instant recordedAt;  // when audit-service stored this record
}
