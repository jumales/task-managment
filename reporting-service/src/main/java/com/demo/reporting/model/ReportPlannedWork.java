package com.demo.reporting.model;

import com.demo.common.dto.WorkType;
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

import java.time.Instant;
import java.util.UUID;

/** Projection of a planned-work row maintained from {@code task-changed} events. */
@Entity
@Table(name = "report_planned_works")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportPlannedWork {

    /** Uses the originating plannedWorkId so inserts are idempotent. */
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type")
    private WorkType workType;

    @Column(name = "planned_hours", nullable = false)
    private long plannedHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
