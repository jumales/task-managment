package com.demo.task.model;

import com.demo.common.dto.TimelineState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a planned or actual milestone timestamp for a task.
 * Each state (PLANNED_START, PLANNED_END, REAL_START, REAL_END) has at most one active entry per task.
 */
@Entity
@Table(name = "task_timelines")
@SQLDelete(sql = "UPDATE task_timelines SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskTimeline {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private TimelineState state;

    /** The point in time associated with this milestone. */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "set_by_user_id", nullable = false)
    private UUID setByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
