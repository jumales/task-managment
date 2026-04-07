package com.demo.audit.model;

import com.demo.common.dto.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Immutable record of a status change on a task, written by the audit-service Kafka consumer. */
@Entity
@Table(name = "audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusAuditRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID taskId;
    private UUID assignedUserId;

    @Enumerated(EnumType.STRING)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private TaskStatus toStatus;

    private Instant changedAt;   // when the change happened in task-service
    private Instant recordedAt;  // when audit-service stored this record
}
