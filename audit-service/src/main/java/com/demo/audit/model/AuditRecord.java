package com.demo.audit.model;

import com.demo.common.dto.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
