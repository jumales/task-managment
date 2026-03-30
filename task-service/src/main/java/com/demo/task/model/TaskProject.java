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

    private Instant deletedAt;
}
