package com.demo.taskmanager.data.dto.enums

import kotlinx.serialization.Serializable

@Serializable
enum class TaskParticipantRole {
    CREATOR,
    ASSIGNEE,
    CONTRIBUTOR,
    WATCHER,
}
