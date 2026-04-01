package com.demo.task.model;

import com.demo.common.dto.WorkType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Records planned hours for a specific work type on a task.
 * One entry per work type per task; immutable once created and cannot be deleted.
 * Creation is restricted to tasks whose status is TODO (planning phase).
 */
@Entity
@Table(name = "task_planned_works")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskPlannedWork {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** User who set the planned hours. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false)
    private WorkType workType;

    /** Estimated hours planned for this work type. */
    @Column(name = "planned_hours", nullable = false)
    private Integer plannedHours;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
