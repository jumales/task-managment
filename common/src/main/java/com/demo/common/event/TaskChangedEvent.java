package com.demo.common.event;

import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.WorkType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Single unified event published to Kafka whenever a tracked field on a Task changes.
 * The {@code changeType} discriminator determines which fields are populated.
 * Every event carries {@code projectId} and {@code taskTitle} so consumers can
 * apply per-project notification templates without an extra service call.
 *
 * <ul>
 *   <li>{@link TaskChangeType#TASK_CREATED}       — task was just created.</li>
 *   <li>{@link TaskChangeType#STATUS_CHANGED}      — {@code fromStatus} / {@code toStatus} are set.</li>
 *   <li>{@link TaskChangeType#COMMENT_ADDED}       — {@code commentId} / {@code commentContent} are set.</li>
 *   <li>{@link TaskChangeType#PHASE_CHANGED}       — {@code fromPhaseId/Name} / {@code toPhaseId/Name} are set.</li>
 *   <li>{@link TaskChangeType#PLANNED_WORK_CREATED} — {@code workLogId}, {@code workLogUserId}, {@code workType}, {@code plannedHours} are set.</li>
 *   <li>{@link TaskChangeType#BOOKED_WORK_CREATED} — {@code workLogId}, {@code workLogUserId}, {@code workType}, {@code bookedHours} are set.</li>
 *   <li>{@link TaskChangeType#BOOKED_WORK_UPDATED} — same fields as BOOKED_WORK_CREATED.</li>
 *   <li>{@link TaskChangeType#BOOKED_WORK_DELETED} — {@code workLogId} is set.</li>
 * </ul>
 */
//TODO to complex - one class for many things. Break to smaller classes. Wrote first plan
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskChangedEvent {

    private UUID taskId;
    private UUID assignedUserId;
    private UUID projectId;
    private String taskTitle;
    private TaskChangeType changeType;
    private Instant changedAt;

    // Populated when changeType == STATUS_CHANGED
    private TaskStatus fromStatus;
    private TaskStatus toStatus;

    // Populated when changeType == COMMENT_ADDED
    private UUID commentId;
    private String commentContent;

    // Populated when changeType == PHASE_CHANGED
    private UUID fromPhaseId;
    private String fromPhaseName;
    private UUID toPhaseId;
    private String toPhaseName;

    // Populated when changeType == WORK_LOG_CREATED / WORK_LOG_UPDATED / WORK_LOG_DELETED
    private UUID workLogId;
    private UUID workLogUserId;
    private WorkType workType;
    private BigInteger plannedHours;
    private BigInteger bookedHours;

    /** Factory for task created events. */
    public static TaskChangedEvent taskCreated(UUID taskId, UUID assignedUserId,
                                               UUID projectId, String taskTitle) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.assignedUserId = assignedUserId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = TaskChangeType.TASK_CREATED;
        e.changedAt = Instant.now();
        return e;
    }

    /** Factory for status change events. */
    public static TaskChangedEvent statusChanged(UUID taskId, UUID assignedUserId,
                                                 UUID projectId, String taskTitle,
                                                 TaskStatus from, TaskStatus to) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.assignedUserId = assignedUserId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = TaskChangeType.STATUS_CHANGED;
        e.changedAt = Instant.now();
        e.fromStatus = from;
        e.toStatus = to;
        return e;
    }

    /** Factory for comment added events. */
    public static TaskChangedEvent commentAdded(UUID taskId, UUID assignedUserId,
                                                UUID projectId, String taskTitle,
                                                UUID commentId, String content) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.assignedUserId = assignedUserId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = TaskChangeType.COMMENT_ADDED;
        e.changedAt = Instant.now();
        e.commentId = commentId;
        e.commentContent = content;
        return e;
    }

    /** Factory for phase change events. */
    public static TaskChangedEvent phaseChanged(UUID taskId, UUID assignedUserId,
                                                UUID projectId, String taskTitle,
                                                UUID fromPhaseId, String fromPhaseName,
                                                UUID toPhaseId, String toPhaseName) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.assignedUserId = assignedUserId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = TaskChangeType.PHASE_CHANGED;
        e.changedAt = Instant.now();
        e.fromPhaseId = fromPhaseId;
        e.fromPhaseName = fromPhaseName;
        e.toPhaseId = toPhaseId;
        e.toPhaseName = toPhaseName;
        return e;
    }

    /** Factory for planned-work created events. */
    public static TaskChangedEvent plannedWorkCreated(UUID taskId, UUID projectId, String taskTitle,
                                                      UUID plannedWorkId, UUID userId,
                                                      WorkType workType, BigInteger plannedHours) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = TaskChangeType.PLANNED_WORK_CREATED;
        e.changedAt = Instant.now();
        e.workLogId = plannedWorkId;
        e.workLogUserId = userId;
        e.workType = workType;
        e.plannedHours = plannedHours;
        return e;
    }

    /** Factory for booked-work created events. */
    public static TaskChangedEvent bookedWorkCreated(UUID taskId, UUID projectId, String taskTitle,
                                                     UUID bookedWorkId, UUID userId,
                                                     WorkType workType, BigInteger bookedHours) {
        return bookedWorkEvent(TaskChangeType.BOOKED_WORK_CREATED, taskId, projectId, taskTitle,
                bookedWorkId, userId, workType, bookedHours);
    }

    /** Factory for booked-work updated events. */
    public static TaskChangedEvent bookedWorkUpdated(UUID taskId, UUID projectId, String taskTitle,
                                                     UUID bookedWorkId, UUID userId,
                                                     WorkType workType, BigInteger bookedHours) {
        return bookedWorkEvent(TaskChangeType.BOOKED_WORK_UPDATED, taskId, projectId, taskTitle,
                bookedWorkId, userId, workType, bookedHours);
    }

    /** Factory for booked-work deleted events. */
    public static TaskChangedEvent bookedWorkDeleted(UUID taskId, UUID projectId, String taskTitle,
                                                     UUID bookedWorkId) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = TaskChangeType.BOOKED_WORK_DELETED;
        e.changedAt = Instant.now();
        e.workLogId = bookedWorkId;
        return e;
    }

    private static TaskChangedEvent bookedWorkEvent(TaskChangeType changeType, UUID taskId,
                                                    UUID projectId, String taskTitle,
                                                    UUID bookedWorkId, UUID userId,
                                                    WorkType workType, BigInteger bookedHours) {
        TaskChangedEvent e = new TaskChangedEvent();
        e.taskId = taskId;
        e.projectId = projectId;
        e.taskTitle = taskTitle;
        e.changeType = changeType;
        e.changedAt = Instant.now();
        e.workLogId = bookedWorkId;
        e.workLogUserId = userId;
        e.workType = workType;
        e.bookedHours = bookedHours;
        return e;
    }
}
