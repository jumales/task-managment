package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Full task view returned by the "view task" action.
 * Contains all task data plus related entities (participants, project, phase, timeline,
 * planned work, booked work, and full assigned-user details).
 * Comments are excluded — they are fetched separately via the comments endpoint.
 */
@Getter
@AllArgsConstructor
public class TaskFullResponse {
    private UUID id;
    /** Auto-generated code combining the project prefix and a sequential number (e.g. "TASK_1"). */
    private String taskCode;
    private String title;
    private String description;
    private TaskStatus status;
    /** Optional classification of the task (FEATURE, BUG_FIXING, etc.). */
    private TaskType type;
    /** Completion percentage in the range 0–100. */
    private int progress;
    /** All users associated with this task, each with their role (CREATOR, ASSIGNEE, VIEWER, REVIEWER). */
    private List<TaskParticipantResponse> participants;
    private TaskProjectResponse project;
    private TaskPhaseResponse phase;
    /** Full profile of the assigned user; null when no user is assigned or user-service is unavailable. */
    private UserDto assignedUser;
    /** Planned and actual start/end timeline entries for this task. */
    private List<TaskTimelineResponse> timelines;
    /** Estimated hours per work type set during the planning phase. */
    private List<TaskPlannedWorkResponse> plannedWork;
    /** Actual hours booked against this task. */
    private List<TaskBookedWorkResponse> bookedWork;
}
