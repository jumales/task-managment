package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    FEATURE,
    BUG_FIXING,
    TESTING,
    PLANNING,
    TECHNICAL_DEBT,
    DOCUMENTATION,
    OTHER,
}
