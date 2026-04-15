package com.demo.task.service;

import com.demo.common.event.TaskChangedEvent;
import com.demo.task.model.Task;
import com.demo.task.model.TaskCodeJob;
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

/**
 * Background scheduler that assigns sequential task codes to tasks created without one.
 *
 * <p>Runs every second with a fixed delay. Fetches pending {@link TaskCodeJob} rows in creation
 * order using {@code FOR UPDATE SKIP LOCKED} so multiple service instances process disjoint sets
 * and never double-assign a code.
 *
 * <p>After a code is assigned the scheduler writes a {@link TaskChangedEvent} to the outbox so
 * the frontend receives a WebSocket push and re-fetches the task to display the new code.
 */
@Service
public class TaskCodeAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TaskCodeAssignmentService.class);

    private final TaskCodeJobRepository jobRepository;
    private final TaskRepository taskRepository;
    private final TaskProjectService projectService;
    private final OutboxWriter outboxWriter;

    public TaskCodeAssignmentService(TaskCodeJobRepository jobRepository,
                                     TaskRepository taskRepository,
                                     TaskProjectService projectService,
                                     OutboxWriter outboxWriter) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.projectService = projectService;
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

    /** Assigns a code to a single pending job and publishes the outbox event. */
    private void assignCode(TaskCodeJob job) {
        String code = projectService.nextTaskCode(job.getProjectId());
        taskRepository.updateTaskCode(job.getTaskId(), code);
        job.setProcessedAt(Instant.now());
        jobRepository.save(job);

        // Load the updated task to populate the outbox event with current field values
        Task task = taskRepository.findById(job.getTaskId()).orElse(null);
        if (task == null) {
            log.warn("Task {} not found after code assignment — skipping outbox write", job.getTaskId());
            return;
        }
        outboxWriter.write(TaskChangedEvent.taskCodeAssigned(
                task.getId(), task.getAssignedUserId(), task.getProjectId(), task.getTitle()));
        log.debug("Assigned code {} to task {}", code, task.getId());
    }
}
