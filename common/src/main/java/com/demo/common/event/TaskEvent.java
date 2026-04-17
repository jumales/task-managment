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
    private String taskCode;
    private String title;
    private String description;
    private TaskStatus status;
    private UUID projectId;
    private String projectName;
    private UUID phaseId;
    private String phaseName;
    private UUID assignedUserId;
    private String assignedUserName;
    private Instant plannedStart;
    private Instant plannedEnd;

    /** Unique ID for this event; used by consumers for idempotent deduplication. */
    private UUID eventId;

    /** Factory for a task-created event carrying full task data. */
    public static TaskEvent created(UUID taskId, String taskCode, String title, String description, TaskStatus status,
                                    UUID projectId, String projectName,
                                    UUID phaseId, String phaseName,
                                    UUID assignedUserId, String assignedUserName,
                                    Instant plannedStart, Instant plannedEnd) {
        return full(TaskEventType.CREATED, taskId, taskCode, title, description, status,
                projectId, projectName, phaseId, phaseName, assignedUserId, assignedUserName,
                plannedStart, plannedEnd);
    }

    /** Factory for a task-updated event carrying full task data. */
    public static TaskEvent updated(UUID taskId, String taskCode, String title, String description, TaskStatus status,
                                    UUID projectId, String projectName,
                                    UUID phaseId, String phaseName,
                                    UUID assignedUserId, String assignedUserName,
                                    Instant plannedStart, Instant plannedEnd) {
        return full(TaskEventType.UPDATED, taskId, taskCode, title, description, status,
                projectId, projectName, phaseId, phaseName, assignedUserId, assignedUserName,
                plannedStart, plannedEnd);
    }

    /** Factory for a task-deleted event carrying only the task ID. */
    public static TaskEvent deleted(UUID taskId) {
        TaskEvent e = new TaskEvent();
        e.taskId = taskId;
        e.eventType = TaskEventType.DELETED;
        e.timestamp = Instant.now();
        e.eventId = UUID.randomUUID();
        return e;
    }

    private static TaskEvent full(TaskEventType type, UUID taskId, String taskCode,
                                  String title, String description,
                                  TaskStatus status, UUID projectId, String projectName,
                                  UUID phaseId, String phaseName,
                                  UUID assignedUserId, String assignedUserName,
                                  Instant plannedStart, Instant plannedEnd) {
        TaskEvent e = new TaskEvent();
        e.taskId = taskId;
        e.eventType = type;
        e.timestamp = Instant.now();
        e.taskCode = taskCode;
        e.title = title;
        e.description = description;
        e.status = status;
        e.projectId = projectId;
        e.projectName = projectName;
        e.phaseId = phaseId;
        e.phaseName = phaseName;
        e.assignedUserId = assignedUserId;
        e.assignedUserName = assignedUserName;
        e.plannedStart = plannedStart;
        e.plannedEnd = plannedEnd;
        e.eventId = UUID.randomUUID();
        return e;
    }
}
