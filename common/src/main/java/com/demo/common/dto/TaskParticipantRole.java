package com.demo.common.dto;

/** The role a user plays on a task. */
public enum TaskParticipantRole {
    /** Set automatically at task creation. */
    CREATOR,
    /** Set via the assignedUserId field on the task; changed only through task update. */
    ASSIGNEE,
    /** Auto-added when a user comments, uploads an attachment, or books hours. */
    CONTRIBUTOR,
    /** Added manually via the Watch action; the only role the user can remove themselves. */
    WATCHER
}
