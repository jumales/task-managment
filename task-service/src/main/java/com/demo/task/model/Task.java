package com.demo.task.model;

import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@SQLDelete(sql = "UPDATE tasks SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    /** Optimistic-locking counter. Hibernate increments this on every UPDATE and appends
     *  {@code WHERE version = ?} to detect concurrent modifications. Never set manually. */
    @Version
    private Long version;

    private String title;
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskType type;

    /** Completion percentage in the range 0–100. */
    private int progress;

    private UUID assignedUserId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "phase_id", nullable = false)
    private UUID phaseId;

    /** Auto-generated code combining the project prefix and a sequential number (e.g. "TASK_1").
     *  Null briefly after creation until the background scheduler assigns it. */
    @Column(name = "task_code")
    private String taskCode;

    private Instant deletedAt;
}
