package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a pending task-code assignment.
 * One row is inserted atomically with task creation whenever a task is saved without a code.
 * The background scheduler {@code TaskCodeAssignmentService} processes these rows in order,
 * assigns sequential codes, and marks each row as processed.
 */
@Entity
@Table(name = "task_code_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCodeJob {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Creation timestamp — used to process jobs in the order tasks were created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Set by the scheduler once the code is successfully assigned; null means pending. */
    @Column(name = "processed_at")
    private Instant processedAt;
}
