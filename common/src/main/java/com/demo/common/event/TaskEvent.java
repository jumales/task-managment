package com.demo.common.event;

import com.demo.common.dto.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Lifecycle event published to the {@code task-events} Kafka topic whenever a task is
 * created, updated, or deleted. Consumed by search-service to keep the Elasticsearch index current.
 *
 * <p>For {@link TaskEventType#DELETED} events only {@code taskId} and {@code eventType} are set;
 * all other fields are {@code null}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskEvent {

    private UUID taskId;
    private TaskEventType eventType;
    private Instant timestamp;

    /** Populated for CREATED and UPDATED events. */
    private String title;
    private String description;
    private TaskStatus status;
    private UUID projectId;
    private String projectName;
    private UUID phaseId;
    private String phaseName;
    private UUID assignedUserId;
    private String assignedUserName;

    /** Factory for a task-created event carrying full task data. */
    public static TaskEvent created(UUID taskId, String title, String description, TaskStatus status,
                                    UUID projectId, String projectName,
                                    UUID phaseId, String phaseName,
                                    UUID assignedUserId, String assignedUserName) {
        return full(TaskEventType.CREATED, taskId, title, description, status,
                projectId, projectName, phaseId, phaseName, assignedUserId, assignedUserName);
    }

    /** Factory for a task-updated event carrying full task data. */
    public static TaskEvent updated(UUID taskId, String title, String description, TaskStatus status,
                                    UUID projectId, String projectName,
                                    UUID phaseId, String phaseName,
                                    UUID assignedUserId, String assignedUserName) {
        return full(TaskEventType.UPDATED, taskId, title, description, status,
                projectId, projectName, phaseId, phaseName, assignedUserId, assignedUserName);
    }

    /** Factory for a task-deleted event carrying only the task ID. */
    public static TaskEvent deleted(UUID taskId) {
        TaskEvent e = new TaskEvent();
        e.taskId = taskId;
        e.eventType = TaskEventType.DELETED;
        e.timestamp = Instant.now();
        return e;
    }

    private static TaskEvent full(TaskEventType type, UUID taskId, String title, String description,
                                  TaskStatus status, UUID projectId, String projectName,
                                  UUID phaseId, String phaseName,
                                  UUID assignedUserId, String assignedUserName) {
        TaskEvent e = new TaskEvent();
        e.taskId = taskId;
        e.eventType = type;
        e.timestamp = Instant.now();
        e.title = title;
        e.description = description;
        e.status = status;
        e.projectId = projectId;
        e.projectName = projectName;
        e.phaseId = phaseId;
        e.phaseName = phaseName;
        e.assignedUserId = assignedUserId;
        e.assignedUserName = assignedUserName;
        return e;
    }
}
