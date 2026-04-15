package com.demo.task.service;

import com.demo.common.event.TaskChangedEvent;
import com.demo.common.event.TaskEvent;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.OutboxEventType;
import com.demo.task.model.Task;
import com.demo.task.model.TaskCodeJob;
import com.demo.task.model.TaskProject;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.TaskCodeJobRepository;
import com.demo.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Background scheduler that assigns sequential task codes to tasks created without one.
 *
 * <p>Runs every second with a fixed delay. Fetches pending {@link TaskCodeJob} rows in creation
 * order using {@code FOR UPDATE SKIP LOCKED} so multiple service instances process disjoint sets
 * and never double-assign a code.
 *
 * <p>After a code is assigned the scheduler writes two outbox events: a {@link TaskChangedEvent}
 * (TASK_CODE_ASSIGNED) so the frontend re-fetches the task, and a {@link com.demo.common.event.TaskEvent}
 * (UPDATED) so reporting and search projections receive the real task code.
 */
@Service
public class TaskCodeAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TaskCodeAssignmentService.class);

    private final TaskCodeJobRepository jobRepository;
    private final TaskRepository taskRepository;
    private final TaskProjectService projectService;
    private final TaskPhaseService phaseService;
    private final TaskTimelineService timelineService;
    private final UserClientHelper userClientHelper;
    private final OutboxWriter outboxWriter;

    public TaskCodeAssignmentService(TaskCodeJobRepository jobRepository,
                                     TaskRepository taskRepository,
                                     TaskProjectService projectService,
                                     TaskPhaseService phaseService,
                                     TaskTimelineService timelineService,
                                     UserClientHelper userClientHelper,
                                     OutboxWriter outboxWriter) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.projectService = projectService;
        this.phaseService = phaseService;
        this.timelineService = timelineService;
        this.userClientHelper = userClientHelper;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Processes all pending code-assignment jobs in creation order.
     * Each job is wrapped in the same transaction: code is incremented, task is updated,
     * job is marked processed, and an outbox event is written — all atomically.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void assignPendingCodes() {
        List<TaskCodeJob> jobs = jobRepository.findPendingForUpdate();
        if (jobs.isEmpty()) return;

        log.debug("Assigning task codes to {} pending job(s)", jobs.size());
        for (TaskCodeJob job : jobs) {
            assignCode(job);
        }
    }

    /** Assigns a code to a single pending job and publishes the outbox events. */
    private void assignCode(TaskCodeJob job) {
        String code = projectService.nextTaskCode(job.getProjectId());
        taskRepository.updateTaskCode(job.getTaskId(), code);
        job.setProcessedAt(Instant.now());
        jobRepository.save(job);

        // Load the task to populate outbox events with current field values.
        // Note: @Modifying JPQL bypasses the L1 cache, so we pass `code` directly instead
        // of relying on task.getTaskCode() which may be stale.
        Task task = taskRepository.findById(job.getTaskId()).orElse(null);
        if (task == null) {
            log.warn("Task {} not found after code assignment — skipping outbox write", job.getTaskId());
            return;
        }

        // Notify the frontend to refresh the task code display
        outboxWriter.write(TaskChangedEvent.taskCodeAssigned(
                task.getId(), task.getAssignedUserId(), task.getProjectId(), task.getTitle()));

        // Propagate the assigned code to reporting and search projections via task-events topic
        outboxWriter.writeTaskEvent(task.getId(), OutboxEventType.TASK_UPDATED,
                buildTaskUpdatedEvent(task, code));

        log.debug("Assigned code {} to task {}", code, task.getId());
    }

    /**
     * Builds a {@link TaskEvent} for the updated task, resolving project name, phase name,
     * assignee display name, and planned dates from their respective services.
     */
    private TaskEvent buildTaskUpdatedEvent(Task task, String code) {
        TaskProject project = projectService.getOrThrow(task.getProjectId());
        UUID phaseId = task.getPhaseId();
        String phaseName = phaseId != null
                ? phaseService.getOrThrow(phaseId).getName().name()
                : null;
        String userName = userClientHelper.resolveUserName(task.getAssignedUserId());
        Instant plannedStart = timelineService.findPlannedStart(task.getId());
        Instant plannedEnd = timelineService.findPlannedEnd(task.getId());
        return TaskEvent.updated(
                task.getId(), code, task.getTitle(), task.getDescription(),
                task.getStatus(), task.getProjectId(), project.getName(),
                phaseId, phaseName, task.getAssignedUserId(), userName,
                plannedStart, plannedEnd);
    }
}
