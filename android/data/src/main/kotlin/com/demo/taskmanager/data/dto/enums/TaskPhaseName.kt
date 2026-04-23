package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

@Serializable
enum class TaskPhaseName {
    PLANNING,
    BACKLOG,
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    TESTING,
    DONE,
    RELEASED,
    REJECTED,
}
