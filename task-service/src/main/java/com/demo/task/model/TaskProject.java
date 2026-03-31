package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Groups tasks under a common project. Every Task must belong to exactly one project.
 */
@Entity
@Table(name = "task_projects")
@SQLDelete(sql = "UPDATE task_projects SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskProject {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Prefix used when auto-generating task codes for this project (e.g. "PROJ_"). Defaults to "TASK_". */
    @Column(nullable = false)
    private String taskCodePrefix;

    /** Counter tracking the next sequential number to assign when a task is created in this project. */
    @Column(nullable = false)
    private int nextTaskNumber;

    private Instant deletedAt;
}
