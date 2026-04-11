package com.demo.reporting.service;

import com.demo.reporting.dto.MyTaskResponse;
import com.demo.reporting.model.ReportTask;
import com.demo.reporting.repository.ReportTaskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Query service for the "My Tasks" report. Reads the local task projection,
 * splits open tasks (not RELEASED/REJECTED) from finished ones, and optionally
 * filters by an {@code updatedAt} cutoff (e.g. last 5 / 30 days).
 */
@Service
public class MyTasksService {

    /** Phase names that represent a fully finished task. */
    private static final Set<String> FINISHED_PHASE_NAMES = Set.of("RELEASED", "REJECTED");

    private final ReportTaskRepository repository;

    public MyTasksService(ReportTaskRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns tasks assigned to {@code userId}.
     * When {@code finished} is {@code true}, only RELEASED/REJECTED tasks are returned;
     * when {@code false} (default), those phases are excluded.
     * When {@code days} is non-null, only tasks updated within the last {@code days} days are included.
     */
    public List<MyTaskResponse> findMyTasks(UUID userId, Integer days, boolean finished) {
        List<ReportTask> rows = finished
                ? findFinished(userId, days)
                : findOpen(userId, days);
        return rows.stream().map(this::toResponse).toList();
    }

    /** Returns open (non-finished) tasks for the user, with an optional recency cutoff. */
    private List<ReportTask> findOpen(UUID userId, Integer days) {
        if (days == null) {
            return repository.findOpenByAssignedUserIdOrderByUpdatedAtDesc(userId, FINISHED_PHASE_NAMES);
        }
        return repository.findOpenByAssignedUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(
                userId, Instant.now().minus(days, ChronoUnit.DAYS), FINISHED_PHASE_NAMES);
    }

    /** Returns finished (RELEASED or REJECTED) tasks for the user, with an optional recency cutoff. */
    private List<ReportTask> findFinished(UUID userId, Integer days) {
        if (days == null) {
            return repository.findByAssignedUserIdAndPhaseNameInOrderByUpdatedAtDesc(
                    userId, FINISHED_PHASE_NAMES);
        }
        return repository.findByAssignedUserIdAndUpdatedAtGreaterThanEqualAndPhaseNameInOrderByUpdatedAtDesc(
                userId, Instant.now().minus(days, ChronoUnit.DAYS), FINISHED_PHASE_NAMES);
    }

    private MyTaskResponse toResponse(ReportTask t) {
        return new MyTaskResponse(
                t.getId(),
                t.getTaskCode(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus(),
                t.getPhaseName(),
                t.getPlannedStart(),
                t.getPlannedEnd(),
                t.getUpdatedAt());
    }
}
