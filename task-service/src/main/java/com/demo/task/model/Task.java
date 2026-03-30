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

    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    @Enumerated(EnumType.STRING)
    private TaskType type;
    /** Completion percentage in the range 0–100. */
    private int progress;
    private UUID assignedUserId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "phase_id")
    private UUID phaseId;

    private Instant deletedAt;
}
