package com.demo.reporting.service;

import com.demo.common.dto.WorkType;
import com.demo.reporting.dto.DetailedHoursResponse;
import com.demo.reporting.dto.ProjectHoursResponse;
import com.demo.reporting.dto.TaskHoursResponse;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportBookedWorkRepository;
import com.demo.reporting.repository.ReportPlannedWorkRepository;
import com.demo.reporting.repository.ReportPlannedWorkRepository.DetailedHoursProjection;
import com.demo.reporting.repository.ReportPlannedWorkRepository.ProjectHoursProjection;
import com.demo.reporting.repository.ReportPlannedWorkRepository.TaskHoursProjection;
import com.demo.reporting.repository.ReportTaskRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregates planned vs booked hours across the reporting projections. Exposes per-task,
 * per-project, and per (user × work-type) breakdowns for the hours report view.
 */
@Service
public class HoursReportService {

    private final ReportPlannedWorkRepository plannedRepository;
    private final ReportBookedWorkRepository bookedRepository;
    private final ReportTaskRepository taskRepository;

    public HoursReportService(ReportPlannedWorkRepository plannedRepository,
                              ReportBookedWorkRepository bookedRepository,
                              ReportTaskRepository taskRepository) {
        this.plannedRepository = plannedRepository;
        this.bookedRepository = bookedRepository;
        this.taskRepository = taskRepository;
    }

    /** Returns one row per task with planned vs booked hours, optionally filtered by project. */
    public List<TaskHoursResponse> byTask(UUID projectId) {
        Map<UUID, Long> planned = toMap(plannedRepository.sumPlannedHoursByTask(projectId));
        Map<UUID, Long> booked  = toMap(bookedRepository.sumBookedHoursByTask(projectId));

        Set<UUID> taskIds = new HashSet<>();
        taskIds.addAll(planned.keySet());
        taskIds.addAll(booked.keySet());
        if (taskIds.isEmpty()) return List.of();

        Map<UUID, ReportTask> tasksById = new HashMap<>();
        taskRepository.findAllById(taskIds).forEach(t -> tasksById.put(t.getId(), t));

        return taskIds.stream()
                .map(id -> {
                    ReportTask task = tasksById.get(id);
                    return new TaskHoursResponse(
                            id,
                            task != null ? task.getTaskCode() : null,
                            task != null ? task.getTitle() : null,
                            planned.getOrDefault(id, 0L),
                            booked.getOrDefault(id, 0L));
                })
                .toList();
    }

    /** Returns one row per project with planned vs booked totals summed across all its tasks. */
    public List<ProjectHoursResponse> byProject() {
        Map<UUID, Long> planned = toProjectMap(plannedRepository.sumPlannedHoursByProject());
        Map<UUID, Long> booked  = toProjectMap(bookedRepository.sumBookedHoursByProject());

        Set<UUID> projectIds = new HashSet<>();
        projectIds.addAll(planned.keySet());
        projectIds.addAll(booked.keySet());
        if (projectIds.isEmpty()) return List.of();

        // Fetch project names with a targeted query — avoids loading the full report_tasks table.
        Map<UUID, String> projectNames = new HashMap<>();
        taskRepository.findProjectNamesByIds(projectIds)
                .forEach(row -> projectNames.put((UUID) row[0], (String) row[1]));

        return projectIds.stream()
                .map(id -> new ProjectHoursResponse(
                        id,
                        projectNames.get(id),
                        planned.getOrDefault(id, 0L),
                        booked.getOrDefault(id, 0L)))
                .toList();
    }

    /** Returns planned vs booked hours for a task broken down by (user, workType). */
    public List<DetailedHoursResponse> detailed(UUID taskId) {
        List<DetailedHoursProjection> plannedRows = plannedRepository.sumPlannedHoursByUserAndType(taskId);
        List<DetailedHoursProjection> bookedRows  = bookedRepository.sumBookedHoursByUserAndType(taskId);

        record UserWorkTypeKey(UUID userId, WorkType workType) {}
        Map<String, UserWorkTypeKey> pairs = new HashMap<>();
        Map<String, Long> planned = new HashMap<>();
        for (DetailedHoursProjection p : plannedRows) {
            String k = key(p.getUserId(), p.getWorkType());
            planned.put(k, p.getTotalHours());
            pairs.put(k, new UserWorkTypeKey(p.getUserId(), p.getWorkType()));
        }
        Map<String, Long> booked = new HashMap<>();
        for (DetailedHoursProjection p : bookedRows) {
            String k = key(p.getUserId(), p.getWorkType());
            booked.put(k, p.getTotalHours());
            pairs.putIfAbsent(k, new UserWorkTypeKey(p.getUserId(), p.getWorkType()));
        }

        return pairs.keySet().stream()
                .map(k -> {
                    UserWorkTypeKey pair = pairs.get(k);
                    return new DetailedHoursResponse(
                            pair.userId(),
                            pair.workType(),
                            planned.getOrDefault(k, 0L),
                            booked.getOrDefault(k, 0L));
                })
                .toList();
    }

    private static Map<UUID, Long> toMap(List<TaskHoursProjection> rows) {
        Map<UUID, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(r.getTaskId(), r.getTotalHours()));
        return out;
    }

    private static Map<UUID, Long> toProjectMap(List<ProjectHoursProjection> rows) {
        Map<UUID, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(r.getProjectId(), r.getTotalHours()));
        return out;
    }

    private static String key(UUID userId, WorkType workType) {
        return userId + "|" + workType;
    }
}
