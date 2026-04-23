package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

@Serializable
enum class WorkType {
    DEVELOPMENT,
    TESTING,
    CODE_REVIEW,
    DESIGN,
    PLANNING,
    DOCUMENTATION,
    DEPLOYMENT,
    MEETING,
    OTHER,
}
