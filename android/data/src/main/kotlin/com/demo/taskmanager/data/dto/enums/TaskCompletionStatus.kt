package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

/** Filter param: FINISHED = RELEASED/REJECTED phase; DEV_FINISHED = DONE phase. */
@Serializable
enum class TaskCompletionStatus {
    FINISHED,
    DEV_FINISHED,
}
