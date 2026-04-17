package com.demo.reporting.repository;

import com.demo.common.dto.WorkType;
import com.demo.reporting.model.ReportPlannedWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data access and aggregation queries for the planned-work projection. */
public interface ReportPlannedWorkRepository extends JpaRepository<ReportPlannedWork, UUID> {

    /** Total planned hours per task, optionally filtered by project. */
    @Query("""
           select p.taskId as taskId, sum(p.plannedHours) as totalHours
             from ReportPlannedWork p
            where (:projectId is null or p.projectId = :projectId)
            group by p.taskId
           """)
    List<TaskHoursProjection> sumPlannedHoursByTask(@Param("projectId") UUID projectId);

    /** Total planned hours per project. */
    @Query("""
           select p.projectId as projectId, sum(p.plannedHours) as totalHours
             from ReportPlannedWork p
            where p.projectId is not null
            group by p.projectId
           """)
    List<ProjectHoursProjection> sumPlannedHoursByProject();

    /** Planned hours for a task broken down by (user, workType). */
    @Query("""
           select p.userId as userId, p.workType as workType, sum(p.plannedHours) as totalHours
             from ReportPlannedWork p
            where p.taskId = :taskId
            group by p.userId, p.workType
           """)
    List<DetailedHoursProjection> sumPlannedHoursByUserAndType(@Param("taskId") UUID taskId);

    interface TaskHoursProjection {
        UUID getTaskId();
        Long getTotalHours();
    }

    interface ProjectHoursProjection {
        UUID getProjectId();
        Long getTotalHours();
    }

    interface DetailedHoursProjection {
        UUID getUserId();
        WorkType getWorkType();
        Long getTotalHours();
    }

    /**
     * Hard-deletes all planned-work projection rows for the given task.
     * Used when a task is archived so the projection table stays clean.
     */
    @Modifying
    @Query(value = "DELETE FROM report_planned_works WHERE task_id = :taskId", nativeQuery = true)
    void deleteAllByTaskId(@Param("taskId") UUID taskId);
}
