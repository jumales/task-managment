package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
}
