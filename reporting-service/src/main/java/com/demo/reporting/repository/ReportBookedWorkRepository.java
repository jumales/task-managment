package com.demo.reporting.repository;

import com.demo.reporting.model.ReportBookedWork;
import com.demo.reporting.repository.ReportPlannedWorkRepository.DetailedHoursProjection;
import com.demo.reporting.repository.ReportPlannedWorkRepository.ProjectHoursProjection;
import com.demo.reporting.repository.ReportPlannedWorkRepository.TaskHoursProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data access and aggregation queries for the booked-work projection. */
public interface ReportBookedWorkRepository extends JpaRepository<ReportBookedWork, UUID> {

    /** Total booked hours per task, optionally filtered by project. Excludes soft-deleted rows via {@code @SQLRestriction}. */
    @Query("""
           select b.taskId as taskId, sum(b.bookedHours) as totalHours
             from ReportBookedWork b
            where (:projectId is null or b.projectId = :projectId)
            group by b.taskId
           """)
    List<TaskHoursProjection> sumBookedHoursByTask(@Param("projectId") UUID projectId);

    /** Total booked hours per project. */
    @Query("""
           select b.projectId as projectId, sum(b.bookedHours) as totalHours
             from ReportBookedWork b
            where b.projectId is not null
            group by b.projectId
           """)
    List<ProjectHoursProjection> sumBookedHoursByProject();

    /** Booked hours for a task broken down by (user, workType). */
    @Query("""
           select b.userId as userId, b.workType as workType, sum(b.bookedHours) as totalHours
             from ReportBookedWork b
            where b.taskId = :taskId
            group by b.userId, b.workType
           """)
    List<DetailedHoursProjection> sumBookedHoursByUserAndType(@Param("taskId") UUID taskId);

    /**
     * Hard-deletes all booked-work projection rows for the given task.
     * Used when a task is archived so the projection table does not grow unbounded with soft-deleted rows.
     */
    @Modifying
    @Query(value = "DELETE FROM report_booked_works WHERE task_id = :taskId", nativeQuery = true)
    void deleteAllByTaskId(@Param("taskId") UUID taskId);
}
