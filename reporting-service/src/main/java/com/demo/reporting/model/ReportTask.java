package com.demo.reporting.model;

import com.demo.common.dto.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection of a task maintained by the reporting read-model.
 * Populated from {@code task-events} Kafka messages. Soft-deleted when a DELETED event arrives.
 */
@Entity
@Table(name = "report_tasks")
@SQLDelete(sql = "UPDATE report_tasks SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportTask {

    /** The task id from task-service; this projection uses the same id (no separate row id). */
    @Id
    private UUID id;

    @Column(name = "task_code")
    private String taskCode;

    private String title;
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "phase_id")
    private UUID phaseId;

    @Column(name = "phase_name")
    private String phaseName;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "assigned_user_name")
    private String assignedUserName;

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_end")
    private Instant plannedEnd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
