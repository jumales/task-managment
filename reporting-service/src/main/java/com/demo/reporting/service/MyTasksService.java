package com.demo.reporting.service;

import com.demo.reporting.dto.MyTaskResponse;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportTaskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Query service for the "My Tasks" report. Reads the local task projection
 * and optionally filters by an {@code updatedAt} cutoff (e.g. last 5 / 30 days).
 */
@Service
public class MyTasksService {

    private final ReportTaskRepository repository;

    public MyTasksService(ReportTaskRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns tasks assigned to {@code userId}. When {@code days} is non-null, only tasks
     * whose {@code updatedAt} falls within the last {@code days} days are returned.
     */
    public List<MyTaskResponse> findMyTasks(UUID userId, Integer days) {
        List<ReportTask> rows = (days == null)
                ? repository.findByAssignedUserIdOrderByUpdatedAtDesc(userId)
                : repository.findByAssignedUserIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(
                        userId, Instant.now().minus(days, ChronoUnit.DAYS));
        return rows.stream().map(this::toResponse).toList();
    }

    private MyTaskResponse toResponse(ReportTask t) {
        return new MyTaskResponse(
                t.getId(),
                t.getTaskCode(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus(),
                t.getPlannedStart(),
                t.getPlannedEnd(),
                t.getUpdatedAt());
    }
}
