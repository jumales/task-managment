package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TaskParticipantRole
import kotlinx.serialization.Serializable

@Serializable
data class ParticipantDto(
    val id: String,
    val userId: String,
    val userName: String?,
    val userEmail: String?,
    val role: TaskParticipantRole,
)

@Serializable
data class ParticipantCreateRequest(
    val userId: String,
    val role: TaskParticipantRole,
)
