package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

@Serializable
enum class TimelineState {
    PLANNED_START,
    PLANNED_END,
    REAL_START,
    REAL_END,
    RELEASE_DATE,
}
