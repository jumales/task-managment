package com.demo.task.model;

import com.demo.common.dto.WorkType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Records actual booked hours for a specific user and work type on a task.
 * A task may have multiple booked-work entries per work type.
 */
@Entity
@Table(name = "task_booked_works")
@SQLDelete(sql = "UPDATE task_booked_works SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskBookedWork {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** User who booked the hours. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false)
    private WorkType workType;

    /** Actual hours booked for this entry; must be greater than zero. */
    @Column(name = "booked_hours", nullable = false)
    private Integer bookedHours;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
