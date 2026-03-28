package com.demo.task.model;

import com.demo.common.dto.WorkType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Records planned and booked hours for a specific user and work type on a task.
 * A task may have multiple work log entries across different users and work types.
 */
@Entity
@Table(name = "task_work_logs")
@SQLDelete(sql = "UPDATE task_work_logs SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskWorkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false)
    private WorkType workType;

    /** Estimated hours planned for this work entry. */
    @Column(name = "planned_hours", nullable = false)
    private BigInteger plannedHours;

    /** Actual hours worked and booked against this entry. */
    @Column(name = "booked_hours", nullable = false)
    private BigInteger bookedHours;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
