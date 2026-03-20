package com.demo.task.model;

import com.demo.common.dto.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    private UUID assignedUserId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "phase_id")
    private UUID phaseId;

    private Instant deletedAt;
}
