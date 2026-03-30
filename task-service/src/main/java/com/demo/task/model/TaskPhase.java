package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A phase (e.g. "Backlog", "In Review", "Released") within a project.
 * Each project may have one default phase, which is automatically assigned to newly created tasks.
 */
@Entity
@Table(name = "task_phases")
@SQLDelete(sql = "UPDATE task_phases SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskPhase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    private String description;

    /** When true, this phase is automatically assigned to new tasks in the project. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    private Instant deletedAt;
}
