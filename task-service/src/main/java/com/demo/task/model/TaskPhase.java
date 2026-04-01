package com.demo.task.model;

import com.demo.common.dto.TaskPhaseName;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A named phase (e.g. BACKLOG, IN_REVIEW, RELEASED) within a project.
 * The active default phase for new tasks is tracked on the owning {@link TaskProject}.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPhaseName name;

    private String description;

    private Instant deletedAt;
}
